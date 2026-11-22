// One Pi session per Lain request. Lain owns history, phases and source admission.
import { pathToFileURL } from 'node:url';
import { mkdir, appendFile } from 'node:fs/promises';
const sdkRoot = process.env.LAIN_PI_SDK || '/home/lain/.local/lib/node_modules/@earendil-works/pi-coding-agent';
const { createAgentSession, DefaultResourceLoader, ModelRuntime, SessionManager, SettingsManager } = await import(pathToFileURL(`${sdkRoot}/dist/index.js`));
const send = value => process.stdout.write(JSON.stringify(value) + '\n');
const pending = new Map();
let request;
let buffer = '';
let receive;
const ready = new Promise(resolve => {
  receive = chunk => {
    buffer += chunk;
    let end;
    while ((end = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, end).replace(/\r$/, ''); buffer = buffer.slice(end + 1);
      const value = JSON.parse(line);
      if (!request) { request = value; resolve(); }
      else if (value.type === 'tool_result') { pending.get(value.id)?.(value.text); pending.delete(value.id); }
    }
  };
  process.stdin.setEncoding('utf8'); process.stdin.on('data', receive);
});
await ready;
let session;
try {
  const messages = request.messages || [{ role: 'system', content: '' }, { role: 'user', content: request.text }];
  // Do not silently collapse explicit conversation roles or add private history.
  if (!(messages.length === 2 && messages[0].role === 'system' && messages[1].role === 'user'))
    throw new Error('Pi adapter currently requires exactly the Lain system/user snapshot pair');
  const cwd = request.workspace;
  await mkdir(cwd, { recursive: true });
  const log = value => appendFile(`${cwd}/events.jsonl`, JSON.stringify(value) + '\n');
  const runtime = await ModelRuntime.create({ authPath: `${cwd}/empty-auth.json`, modelsPath: `${cwd}/empty-models.json` });
  runtime.registerProvider('lain-local', {
    baseUrl: request.endpoint || 'http://127.0.0.1:18320/v1', api: 'openai-completions',
    apiKey: 'local-no-credential', authHeader: false,
    models: [{ id: 'qwen3.8-flash-next', name: 'Local Qwen', reasoning: true, input: ['text'],
      contextWindow: 131072, maxTokens: 8192, compat: { supportsDeveloperRole: false, maxTokensField: 'max_tokens', supportsReasoningEffort: false }, cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 } }],
  });
  const loader = new DefaultResourceLoader({
    cwd, agentDir: cwd, noExtensions: true, noSkills: true, noPromptTemplates: true, noThemes: true,
    agentsFilesOverride: () => ({ agentsFiles: [] }), systemPromptOverride: () => messages[0].content,
    appendSystemPromptOverride: () => [],
    extensionFactories: [pi => { pi.on('before_agent_start', () => ({ systemPrompt: messages[0].content })); }],
  });
  await loader.reload();
  let toolCount = 0, calls = 0;
  const inspect = {
    name: 'inspect_lain', label: 'Inspect Lain',
    description: 'Read bounded current Lain bindings. Empty name lists matching names; a name returns immediate value kind and input/output edges. No writes or source admission.',
    parameters: { type: 'object', properties: { name: { type: 'string' }, query: { type: 'string' } }, additionalProperties: false },
    execute: async (id, params) => {
      if (++toolCount > 8) throw new Error('Lain inspection budget exhausted');
      const text = await new Promise(resolve => { pending.set(id, resolve); send({ type: 'tool', id, name: 'inspect_lain', arguments: params }); });
      return { content: [{ type: 'text', text }], details: {} };
    },
  };
  ({ session } = await createAgentSession({ cwd, agentDir: cwd, modelRuntime: runtime,
    model: runtime.getModel('lain-local', 'qwen3.8-flash-next'), thinkingLevel: 'medium',
    tools: ['inspect_lain'], customTools: [inspect], resourceLoader: loader,
    sessionManager: SessionManager.inMemory(cwd),
    settingsManager: SettingsManager.inMemory({ compaction: { enabled: false }, retry: { enabled: false } }),
  }));
  const stream = session.agent.streamFunction;
  session.agent.streamFunction = (model, context, options) => {
    if (++calls > 4) throw new Error('Pi inference budget exhausted');
    return stream(model, context, { ...options, temperature: 0, maxTokens: 8192,
      onPayload: async payload => {
        const result = { ...payload, temperature: 0, max_tokens: 8192,
          chat_template_kwargs: { enable_thinking: true, reasoning_effort: 'medium' }, thinking_token_budget: 4096 };
        if ((result.messages.find(m => m.role === 'system' || m.role === 'developer')?.content || '') !== messages[0].content) throw new Error('Pi changed Lain system context');
        await log({ type: 'provider_payload', payload: result }); return result;
      } });
  };
  const events = [];
  session.subscribe(event => { if (['message_end', 'tool_execution_end', 'agent_settled'].includes(event.type)) events.push(event); });
  await session.prompt(messages[1].content);
  await session.waitForIdle();
  for (const event of events) await log(event);
  const last = [...session.messages].reverse().find(m => m.role === 'assistant');
  if (!last || last.stopReason !== 'stop') throw new Error(`Pi did not produce a complete response: ${last?.stopReason}: ${last?.errorMessage || ''}`);
  const text = last.content.filter(c => c.type === 'text').map(c => c.text).join('');
  const thinking = last.content.filter(c => c.type === 'thinking').map(c => c.thinking).join('');
  send({ type: 'reply', kind: 'reply', text, thinking, 'finish-reason': 'stop', 'truncated?': false, 'pi-calls': calls, 'pi-tools': toolCount });
} catch (error) {
  send({ type: 'error', message: String(error), stack: error.stack }); process.exitCode = 1;
} finally {
  session?.dispose(); process.stdin.off('data', receive); process.stdin.destroy();
}
