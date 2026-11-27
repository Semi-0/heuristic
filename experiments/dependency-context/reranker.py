"""Line-oriented CPU worker for Qwen's yes/no relevance score."""
import json
import sys
import time

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

started = time.monotonic()
torch.set_num_threads(2)
tokenizer = AutoTokenizer.from_pretrained(sys.argv[1], padding_side="left", local_files_only=True)
model = AutoModelForCausalLM.from_pretrained(sys.argv[1], dtype=torch.bfloat16, local_files_only=True).eval()
no, yes = tokenizer.convert_tokens_to_ids(["no", "yes"])
prefix = ("<|im_start|>system\nJudge whether the Document meets the requirements based on the Query "
          "and the Instruct provided. Note that the answer can only be \"yes\" or \"no\".<|im_end|>\n<|im_start|>user\n")
suffix = "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
retrieval_instruction = "Select the archived record most directly needed to answer the question, respecting the requested order revision."
print(json.dumps({"ready": True, "load_seconds": time.monotonic() - started}), flush=True)
with torch.inference_mode():
    for line in sys.stdin:
        request = json.loads(line)
        started = time.monotonic()
        scores = []
        tokens = 0
        instruction = request.get("instruction", retrieval_instruction)
        docs = request["documents"]
        for i in range(0, len(docs), 4):
            batch = docs[i:i+4]
            texts = [f"{prefix}<Instruct>: {instruction}\n<Query>: {request['question']}\n<Document>: {d['label']}\n{d['body']}{suffix}" for d in batch]
            inputs = tokenizer(texts, padding=True, return_tensors="pt", add_special_tokens=False)
            tokens += inputs.attention_mask.sum().item()
            logits = model(**inputs, use_cache=False, logits_to_keep=1).logits[:, -1, [no, yes]]
            for doc, score in zip(batch, logits.float().softmax(dim=-1)[:, 1].tolist()):
                scores.append({"id": doc["id"], "score": score})
        scores.sort(key=lambda s: -s["score"])
        print(json.dumps({"scores": scores, "input_tokens": tokens, "seconds": time.monotonic()-started}), flush=True)
