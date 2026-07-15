# Phase 5 Acceptance Checklist

| Criterion | Status | Evidence |
|---|---|---|
| Unsupported facts cannot pass validation | PASS | ResumeDocumentContractTest |
| PDF text is selectable | PASS | PDFTextStripper assertion |
| DOCX rendering works | PASS | XWPF paragraph assertion |
| Approved/locked versions cannot be edited | PASS | Append-only revisions and ResumeVersionPolicy test |
| Five recommended variants are available | PASS | Planner/API/UI and size assertion |
| Evidence and missing requirements are visible | PASS | TailoredResumesComponent test |
| Backend/frontend non-Docker gates pass | PASS | 29 backend tests; 10 frontend tests; lint/build |
| Docker-dependent integration and migration | PASS | Maven verify and authenticated real-stack PDF/DOCX workflow passed |
