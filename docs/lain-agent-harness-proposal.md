# Lain Agent Harness

Persistent Computational Networks as Executable Agent Memory

**Status:** Research and Design Proposal  
**Version:** 0.1  
**Foundation:** Lain — propagator-based programming language and runtime  
**Recorded:** November 5, 2026

This proposal records the intended architecture, not completed implementation.
It was supplied by the project author. Formatting and the opening design notes
have been lightly edited for readability. The relationship to the current
experiment is recorded separately in [Experiment intention and next steps](experiment-intention.md).

## Guiding ideas

Every expression shall have a view.

Lain with provenance for skills can let an agent backtrack decisions through
their dependencies, and therefore strategically select the search space for
problem solving.

Timestamped provenance allows an agent to understand the consequences of its
actions and recursively optimize solution speed. Time and space consequences
would be foundational preconditions for that optimization.

Action networks allow an agent to automate repetitive actions.

## 1. Abstract

Contemporary LLM agent harnesses primarily organize computation around sequences
of messages, model invocations, and tool calls. Although these interactions may
produce reusable solutions, their computational history is typically preserved
as text, summaries, or isolated artifacts.

Consequently, agents frequently reconstruct solutions to previously encountered
problems, repeating investigations and tool operations even when relevant
computational experience already exists.

This proposal explores an alternative architecture in which every agent
invocation is represented as a persistent Lain computational network.

Messages, tool calls, intermediate observations, and child-agent invocations
participate in the construction and evolution of this network. Each invocation
creates a computational environment whose structure, dependencies, and results
can survive beyond the lifetime of the model's context window.

These networks collectively form an enduring, queryable, and potentially
executable computational history.

Historical networks can be retrieved through natural-language semantic matching
and structural queries using Datalog. Retrieved networks may be inspected,
referenced, forked, resumed, or composed into subsequent computations.

By using Lain as the underlying execution model, the harness inherits facilities
for dependency tracking, incremental propagation, lexical composition,
subnetwork management, and native observability.

The research hypothesis is that preserving the executable structure of prior
agent computations can improve cumulative problem-solving efficiency,
particularly for long-running software engineering tasks involving recurring
problems and shared dependencies.

## 2. Problem Statement

### 2.1 Agents have limited computational continuity

Current agent systems typically distinguish between an agent's present execution
and its historical memory.

During execution, an agent can access an active conversation context, invoke
tools, interpret their results, and construct temporary plans.

Once an execution ends, its computational organization is often lost. The
remaining information may include:

- Conversation transcripts and tool-call logs.
- Compressed summaries of previous interactions.
- Files, source-code changes, and external artifacts.
- Retrieved documents or indexed memories.
- Explicitly authored reusable skills and scripts.

These representations preserve useful information but do not necessarily
preserve the computational relationships through which that information was
produced.

For example, an agent may previously have investigated a dependency graph,
identified a performance bottleneck, tested several hypotheses, and constructed
a successful solution.

When encountering a related problem, a new agent invocation may retrieve the
previous conclusion yet still need to reconstruct the investigation, identify
relevant dependencies, and rediscover the operational procedure.

The underlying problem is a lack of reusable computational continuity across
invocations.

### 2.2 Textual retrieval does not guarantee computational reuse

Retrieval-augmented generation can help agents locate relevant previous
interactions. However, retrieving a description of a computation is different
from recovering the computation itself.

Textual memory generally requires the model to reinterpret information and
translate it into executable operations.

This introduces redundant reasoning, additional token consumption, and
opportunities for mistakes.

It also limits the ability to query historical computations by their actual
structure, dependencies, environment, or observed behavior.

### 2.3 Isolated agent invocations inhibit cumulative problem-solving

In many agent harnesses, child agents and tool calls are scoped to individual
tasks.

Even when a child agent discovers a generally useful method, its execution
context is usually discarded or reduced to a textual result.

As a result, computational capabilities discovered during one task are not
automatically available as structured resources for subsequent tasks.

For long-running projects, the harness should allow agents to accumulate and
reuse computational structures across tasks.

## 3. Design Hypothesis

The central proposal is:

> An agent invocation is a persistent computational network that may construct,
> extend, and compose other computational networks.

Each invocation is itself a Lain network.

The invocation contains or references its environment, messages, model
evaluations, tool interactions, observations, computational dependencies, and
results.

The network may continue to exist after the corresponding model call terminates.

Future invocations can retrieve previously constructed networks, inspect their
internal structure, and reuse them as part of new computations.

This produces a different relationship between agent execution and agent memory:

> Agent memory becomes a persistent collection of computational environments
> and executable structures.

The harness should support the following properties:

1. Persistence: Network identity and structure survive individual model invocations.
2. Composition: Existing networks can be referenced and incorporated into new networks.
3. Lexical inheritance: Child networks can access bindings provided by their lexical parents.
4. Retrievability: Networks can be discovered through semantic and structural queries.
5. Observability: Network states, dependencies, and computational histories can be inspected.
6. Incrementality: Changes can propagate through relevant computational dependencies when permitted.
7. Reusability: Previously solved problems can contribute directly to new computations.
8. Provenance: The origin, assumptions, and validation status of historical results remain identifiable.

The expected benefit is reduced redundant computation and improved continuity
between related agent tasks.

## 4. Conceptual Architecture

### 4.1 The invocation as a network

Instead of treating an agent invocation as a temporary control-flow operation,
the harness represents it as a network containing a collection of computational
elements.

A conceptual invocation may include:

- An identity and persistent network root.
- A lexical environment.
- An input task or message.
- References to historical networks.
- An LLM evaluation propagator.
- Tool-call and tool-result structures.
- Intermediate observations.
- Child-network references.
- Output cells and completion status.
- Provenance and dependency information.

Conceptually:

```text
Agent Invocation Network
│
├── Identity
├── Lexical Environment
├── Input Message / Task
│
├── Retrieved Networks
│   ├── Historical Network A
│   └── Historical Network B
│
├── Model Evaluation
│   └── Proposed Operations
│
├── Tool Interactions
│   ├── Tool Invocation
│   └── Observed Result
│
├── Child Networks
│   ├── Child Agent A
│   └── Child Agent B
│
├── Results
└── Provenance / Dependencies
```

This diagram describes conceptual relationships, not a mandatory physical
storage layout.

The model invocation itself participates in the propagator system. Its inputs
and outputs are represented within the surrounding network.

An invocation may generate messages, request tool operations, construct new
networks, or modify bindings through explicitly defined interfaces.

### 4.2 Messages and tool calls as network construction

Every message and tool interaction contributes to network construction.

A message can introduce new information, dependencies, or requested computations.

A tool call can establish a computational relationship between its inputs,
invocation, and observed output.

For example:

```text
Task
 │
 ▼
Model Invocation
 │
 ├── Read Source File
 │      │
 │      ▼
 │   File Observation
 │      │
 │      ▼
 ├── Analyze Dependencies
 │      │
 │      ▼
 └── Construct Solution Subnetwork
        │
        ▼
     Verified Result
```

The sequence is preserved as a connected computational structure.

The harness can later identify which inputs, observations, and dependencies
contributed to a particular result.

Tool invocation records must distinguish stored observations from operations
that would cause new external side effects.

### 4.3 Long-running network construction

An agent is understood as a network constructor operating within a persistent
computational environment.

During execution, it may:

- Construct new cells and propagators.
- Introduce new lexical environments.
- Spawn child-agent networks.
- Reference previously constructed networks.
- Establish dependencies among computations.
- Publish reusable results through explicitly defined subroots.

Rather than producing a single disposable execution trace, an invocation extends
the available computational history.

The accumulated network may be regarded as an evolving computational workspace.

This workspace can contain completed executions, dormant networks, active
computations, and reusable subnetwork interfaces.

These categories require explicit lifecycle semantics.

## 5. Lexical Environments and Child Networks

### 5.1 Lexical inheritance

Each invocation runs within a Lain lexical environment.

Child invocations may inherit access to bindings in their parent environment.

Conceptually:

```text
Root Environment
│
├── Network A
│   ├── Local Bindings
│   └── Child Network A1
│       └── Inherits A's Environment
│
└── Network B
    ├── Local Bindings
    └── Child Network B1
        └── Inherits B's Environment
```

This enables child networks to reuse existing computational resources without
reconstructing the entire execution context.

Lexical inheritance should preserve the distinction between reading a parent's
bindings and modifying shared state.

### 5.2 Persistent lexical structures

One proposed implementation is to represent lexical environments as persistent
linked structures.

Each child environment references its parent and stores only its own bindings.

Conceptually:

```text
Child Frame
    ↓ parent
Parent Frame
    ↓ parent
Root Frame
```

This representation supports structural sharing and inexpensive creation of new
environments.

It also preserves a traceable lexical ancestry for historical invocations.

The exact representation of network structure remains open. A linked-list-based
representation may serve as a foundational structural primitive, while graph
indexes and other specialized structures support dependency traversal and
querying.

### 5.3 Network references and composition

A child network may reference an existing network by identity.

This should avoid unnecessary duplication of historical computational structures.

For example:

```text
Historical Network: Dependency Analysis
                │
          references
                │
                ▼
New Network: Incremental Testing
                │
                ▼
New Network: Agent Coordination
```

The harness should support references to stable historical versions, as well as
explicitly managed live references where appropriate.

Network composition should define how bindings, identities, dependencies, and
effects behave when one network is reused in another.

## 6. Persistent Executable History

### 6.1 Historical networks as reusable computations

Every invocation contributes to a persistent history composed of executable
network structures.

This history may contain:

- Successful solutions.
- Failed investigations.
- Partial computations.
- Reusable algorithms.
- Tool observations.
- Validated procedures.
- Domain-specific problem-solving strategies.
- Relationships between prior and current tasks.

The objective is to make previous computational work accessible without requiring
the agent to reproduce every step.

A historical network should preserve sufficient information to identify its
inputs, assumptions, dependencies, outcomes, and reusable interfaces.

### 6.2 Reuse operations

A retrieved network may support several operations.

- **Inspect:** Examine cells, propagators, dependencies, observations, and results.
- **Reference:** Use a historical result or network interface without duplicating its structure.
- **Fork:** Create a new computational branch that shares immutable historical structure.
- **Resume:** Continue a suspended or incomplete computation from a persisted state, subject to lifecycle and validity constraints.
- **Instantiate:** Create a new execution from a reusable network template.
- **Compose:** Combine multiple network interfaces into a new computation.
- **Replay:** Reconstruct a historical computation under controlled conditions, with external effects governed separately.

These operations should have distinct semantics.

A stored historical execution should not automatically be treated as safe to
rerun in a new environment.

### 6.3 Reusable computational boundaries

A complete invocation may contain large amounts of incidental work.

Therefore, retrieval of an entire invocation may be unnecessary or inefficient.

Lain subroots could provide explicit boundaries around reusable portions of a
network.

For example:

```text
Historical Investigation
│
├── Exploratory Observations
├── Failed Hypotheses
├── Source Dependencies
│
└── Reusable Subroot
    ├── Inputs
    ├── Preconditions
    ├── Computation
    ├── Outputs
    └── Validation
```

A future invocation could retrieve and compose the reusable subroot while
retaining access to the broader historical context for inspection.

Subroots therefore become potential units of computational reuse.

## 7. Network Discovery and Retrieval

### 7.1 Network descriptions

Each persistent network should have an associated description.

Descriptions provide an interface for approximate natural-language retrieval.

A description may summarize:

- The problem addressed.
- The network's computational purpose.
- Its inputs and outputs.
- Its relevant domain.
- Its observed outcome.
- Its reusable capabilities.

These descriptions could be produced during network construction and refined
following verification.

### 7.2 Semantic retrieval

An agent can issue an ambiguous natural-language query to discover potentially
relevant historical networks.

For example:

> Find an earlier solution for incremental dependency tracking.

A semantic retrieval system returns candidate networks based on similarity
between the query and stored descriptions.

Semantic matching is useful when the agent does not know the exact structure or
identity of a previous solution.

### 7.3 Structural retrieval through Datalog

Because historical computations are represented as networks, the harness can
also expose their structural relationships as queryable facts.

Datalog can be used to search by:

- Dependency relationships.
- Network ancestry.
- Tool usage.
- Cell or propagator types.
- Input and output relationships.
- Validation status.
- Shared computational substructures.

Illustrative query:

```clojure
[:find ?network
 :where
 [?network :network/status :verified]
 [?network :network/uses ?solver]
 [?solver :component/type :dependency-tracker]]
```

The above syntax is illustrative and does not prescribe Lain's eventual query
API.

### 7.4 Hybrid retrieval

Semantic and structural retrieval can complement one another.

A possible retrieval procedure is:

1. Interpret the current task.
2. Search descriptions for relevant historical networks.
3. Query structural relationships to narrow candidates.
4. Inspect assumptions, dependencies, and validation status.
5. Select reusable subroots or networks.
6. Reference, fork, instantiate, or resume selected computations.
7. Record the reuse relationship in the current invocation network.

The objective is to minimize unnecessary reconstruction while ensuring that
previously computed results remain applicable to the current task.

## 8. Native Observability

Lain's computational model provides a foundation for inspecting agent behavior
through its network representation.

Because messages, tool calls, model invocations, and derived results participate
in a shared computational structure, the harness can expose:

- Invocation and child-network ancestry.
- Computational dependencies.
- Information propagation.
- Relevant intermediate states.
- Tool-call provenance.
- Historical network references.
- Subroot boundaries.
- Conflicting assumptions or inconsistent results.

Rather than relying exclusively on textual execution logs, debugging tools can
inspect the computational relationships between agent operations.

For example, when an agent reaches an incorrect conclusion, the harness could
trace the result to its contributing observations, dependencies, and earlier
invocations.

This could also support visualization, interactive inspection, and
dependency-directed debugging.

Native observability is therefore a property of the execution model, rather
than an entirely separate logging mechanism.

## 9. Incrementality and Reactivation

An important research question concerns the lifetime of historical networks.

At least two lifecycle models are possible.

### 9.1 Dormant persistent networks

Completed invocations retain their structures and states without continuing to
execute.

Future invocations explicitly retrieve and reactivate relevant networks.

This model offers more predictable resource consumption and side-effect control.

### 9.2 Live persistent networks

Selected network components remain active.

When an upstream dependency changes, the runtime may propagate new information
through the network and update affected results.

This creates the possibility that historical computations continue contributing
to future work without requiring explicit rediscovery.

However, continuously active agent networks could introduce substantial
computational cost, repeated model calls, and unintended tool execution.

### 9.3 Proposed initial direction

The initial implementation should preserve all network structures while keeping
completed invocations dormant by default.

Pure computational components may support selective reactivation.

LLM evaluations and effectful tool operations should require explicit scheduling
or authorization.

This preserves the possibility of a reactive agent environment without requiring
all historical computations to remain active.

## 10. Effect Management and Historical Validity

### 10.1 Pure computation and external effects

A persistent executable history must distinguish between recomputing an
internal result and repeating an external action.

For example, recalculating a dependency relation may be safe.

Repeating a file deletion, sending an email, or executing a deployment may not be.

The harness should separate effect descriptions, effect execution, and observed
effect results.

Historical tool calls should remain inspectable without being automatically
reexecuted.

### 10.2 Dependency validity

A previously successful solution may depend on an environment that has since
changed.

For example, a historical network could reference an outdated version of a source
file or an obsolete test result.

Therefore, stored networks should retain information about the assumptions and
inputs under which their results were produced.

Before reuse, the harness should evaluate whether relevant dependencies remain
valid.

Lain's dependency tracking and truth-maintenance mechanisms may offer a
foundation for managing these relationships.

The exact integration with historical versions and non-monotonic external state
requires further design.

### 10.3 Trust boundaries

Historical networks may contain untrusted external observations, model-generated
hypotheses, or previously authorized actions.

Persistence should not turn these into unconditional authority for future
invocations.

Network reuse must preserve provenance, access permissions, and validation state.

Child agents should receive capabilities appropriate to their task and lexical
environment.

## 11. Storage and Structural Representation

The storage model must support persistent network identity, structural sharing,
efficient retrieval, and selective reactivation.

A possible architecture separates the logical representation from its indexes.

### 11.1 Structural layer

Stores:

- Cells and propagators.
- Network and subroot identities.
- Lexical parent relationships.
- Network references.
- Invocation records.
- Persistent state versions.
- Dependency edges.

Persistent linked structures are a candidate for representing lexical
environments and immutable historical extensions.

### 11.2 Query layer

Maintains indexes supporting:

- Datalog queries.
- Network ancestry.
- Dependency traversal.
- Subroot discovery.
- Historical version lookup.

### 11.3 Semantic layer

Maintains descriptions and associated semantic representations for approximate
retrieval.

The semantic index maps candidate matches back to persistent network identities.

### 11.4 Execution layer

Provides the runtime operations necessary to instantiate, resume, fork, and
execute network components.

This layer also manages computational scheduling, resource limits, and external
effects.

These layers can share an underlying data model without requiring identical
physical representations.

## 12. Example Use Case: Cumulative Software Engineering

Consider a coding agent working on a large software project.

### Invocation A: Dependency analysis

The agent investigates the project's dependency structure.

It constructs a network representing:

- Relevant files.
- Dependency relationships.
- Tool observations.
- Analysis operations.
- Identified dependency bottlenecks.

A verified dependency-analysis subroot is retained.

### Invocation B: Incremental testing

Later, the agent is asked to reduce the test suite's execution time.

It searches historical networks and discovers the dependency-analysis subroot.

Rather than rediscovering the project's dependency relationships, it references
the previous network, validates its assumptions, and extends it with a
test-selection computation.

The result is stored as another persistent network.

### Invocation C: Parallel agent coordination

A subsequent task involves distributing work across several coding agents.

The invocation retrieves the dependency-analysis and incremental-testing networks.

It composes their reusable interfaces to identify suitable task partitions and
relevant tests.

The new invocation produces an agent-coordination network referencing both
historical computations.

The project now contains an accumulating collection of computational capabilities.

This example illustrates the intended benefit: later agent tasks may reuse the
operational structures produced by earlier tasks, reducing redundant
investigation and computation.

## 13. Implementation Strategy

### Phase 1 — Persistent invocation representation

Implement agent invocations as Lain networks.

Support:

- Invocation identity.
- Network-based message representation.
- Tool-call and tool-result records.
- Lexical parent references.
- Child-network construction.
- Persistent state and history.

The first milestone is an invocation that can terminate and later be reopened
with its computational structure intact.

### Phase 2 — Network discovery

Introduce network descriptions and hybrid retrieval.

Support semantic matching and Datalog queries over historical structures.

Agents should be able to discover earlier invocations and inspect their results
and dependencies.

### Phase 3 — Reusable subroots

Define reusable computational interfaces within existing networks.

Support referencing, forking, and instantiating selected subnetworks.

Preserve provenance and distinguish historical observations from current
executable operations.

### Phase 4 — Dependency-aware reuse

Integrate validation of historical inputs and assumptions.

Explore truth-maintenance mechanisms for identifying stale or conflicting results.

Support selective recomputation of pure network components.

### Phase 5 — Reactive and cumulative execution

Investigate controlled reactivation of historical networks.

Explore whether persistent computational dependencies can support incremental
updates across agent invocations.

Evaluate the resource and correctness implications of maintaining long-lived
executable structures.

## 14. Evaluation

The architecture should be evaluated against conventional agent harnesses using
textual history, retrieval-augmented memory, and explicit reusable skills.

The primary research question is:

> Can persistent executable computational history reduce the cost of solving
> related problems across multiple agent invocations?

Suggested evaluation dimensions include:

- **Computational reuse:** How much previously completed work can be reused without reinterpretation or reexecution?
- **Token efficiency:** Does network-based reuse reduce model-token consumption for recurring or structurally related tasks?
- **Task completion time:** Does the harness reduce end-to-end latency?
- **Correctness:** Do reused computations improve solution reliability, or introduce errors from outdated assumptions?
- **Retrieval quality:** Can the system reliably identify relevant historical networks and subroots?
- **Storage overhead:** How quickly does persistent network history grow?
- **Execution overhead:** What are the costs of maintaining, indexing, and validating historical dependencies?
- **Long-horizon performance:** Does the system become more effective as related tasks accumulate?

A useful benchmark would contain sequences of related software engineering
tasks with reusable intermediate solutions.

The evaluation should compare an agent using persistent computational history
with one using an equivalent textual history and conventional retrieval.

To isolate the benefit of executable memory, both systems should have access to
comparable historical information and tools.

## 15. Open Research Questions

**Network granularity**

Should every message and tool call create an independent subnetwork, or should
some operations extend an existing invocation network directly?

**Reusable capability identification**

How can the harness identify meaningful reusable subroots without requiring the
agent to manually annotate every computational step?

**Historical network validity**

How should historical results respond when their original assumptions or
external dependencies change?

**Lexical scope and sharing**

Which bindings should child agents inherit, and how should shared state be
managed across concurrent invocations?

**Structural representation**

Which parts of the system benefit from persistent linked structures, and which
require graph-oriented or indexed representations?

**Execution lifecycle**

Should historical networks remain dormant by default, or can selected propagators
safely remain active?

**Network growth**

How can the harness prevent unbounded growth while preserving useful computational
history? Can structurally equivalent networks be shared or deduplicated?

**Model computation**

What exactly is persisted for an LLM evaluation, and when should a previous model
result be reused rather than requesting a new evaluation?

**Retrieval and composition**

How should the system determine whether a historical subnetwork is compatible
with the current lexical environment and task?

## 16. Expected Contributions

If successful, the project could contribute:

1. A propagator-based execution model for persistent agent invocations.
2. A unified representation of agent execution and computational memory.
3. Hybrid semantic and structural retrieval over historical agent computations.
4. A mechanism for reusing, composing, and extending previously constructed computational networks.
5. Native dependency-oriented observability for long-running agent systems.
6. An experimental foundation for cumulative, incrementally reusable agent computation.

## 17. Conclusion

The proposed Lain agent harness treats agent invocations as persistent
computational networks.

Every invocation contributes to an evolving collection of executable structures,
preserving not only its textual history and results, but also the computational
relationships through which those results were produced.

By combining lexical environments, propagator networks, persistent structural
sharing, semantic retrieval, Datalog querying, and explicit subroot interfaces,
the harness aims to make past problem-solving activity available as reusable
computation.

The motivating hypothesis is that agents should be able to accumulate computational
capabilities through experience, allowing future tasks to build directly on
previously constructed solutions.

In this architecture, agent history is part of the execution environment itself.

The long-term objective is an agent system whose accumulated work can be inspected,
queried, composed, and selectively reactivated—supporting computational continuity
across tasks and model context windows.
