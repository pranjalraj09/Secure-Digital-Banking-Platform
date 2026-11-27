# FINCORE Nexus — Frontend Demo

Frontend-only project for the FINCORE Nexus Enterprise Digital Banking Platform.

## Included
- Existing Milestone 1 customer and admin account dashboards, account management, simulated deposits/withdrawals/transfers, transaction history and statements.
- Milestone 2 loan application, loan origination/review, indicative credit assessment, EMI calculator and repayment schedule, simulated EMI payments, disbursement status and loan administration.
- Milestone 3 customer IMPS/NEFT/UPI payment simulation, beneficiary management, payment references and downloadable demo receipts; admin payment monitoring, risk flags, hold/approve/reject controls, settlement status and visual Debit → Credit → Notify Saga.
- Milestone 4 customer KYC submission/status and admin KYC review, document viewing when file data is available, review notes and audit events.
- Existing customer/admin separation, theme and responsive layout are retained.

## Run locally
Requirements: Node.js and npm.

```bash
npm install
npm run dev
```

Open the local URL printed by Vite (usually `http://127.0.0.1:5173`).

## Demo logins
- Customer: `demo@fincore.test` / `Demo@1234`
- Admin: `admin@fincore.test` / `Admin@1234`

## Frontend demo limitations
- Loan applications, credit assessment, loan approval/disbursement, EMI payments, payment rails, fraud scores, settlement/reconciliation and KYC review are frontend demonstrations using mock data.
- IMPS, NEFT and UPI are not connected to real payment networks. No real funds are transferred.
- The payment Saga is a visual demonstration; no actual debit, credit or notification is performed.
- OCR, face match and liveness are not connected to verification providers. KYC review decisions are demo actions.
- Uploaded KYC documents are handled only in browser memory for the demo. Do not upload real Aadhaar, PAN, financial or other sensitive documents.
- Demo state is held in memory and resets on refresh. No backend APIs are enabled (`USE_BACKEND=false`).
- Backend integration is still required for persistence, authentication/authorization, authoritative credit assessment, financial calculations and ledger operations, payment processing, fraud detection, settlement, OCR/biometric checks and immutable audit records.

## Backend integration
The existing frontend API placeholder is retained. Coordinate endpoint contracts with the Spring Boot developer before enabling backend mode. No Java/backend files have been changed or included in this frontend update.
