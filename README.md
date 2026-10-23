# FinCore Nexus — Original UI Preserved

This project uses the **original uploaded HTML file as `index.html`** so the interface, styling, and in-page React demo logic remain the same as the supplied prototype. Vite is only used to serve the file locally; the UI still loads React, Recharts, Babel, and Tailwind from CDNs as the original HTML did.

> **Demo only:** balances, users, and transactions are fictional and stored in browser memory.

## Requirements
- Node.js and npm installed
- Internet connection (the original HTML loads its UI libraries from CDNs)

## Run on Windows
1. Extract this ZIP file.
2. Open the extracted `fincore-nexus-project` folder in VS Code.
3. Open **Terminal → New Terminal**.
4. Run:
   ```bash
   npm install
   npm run dev
   ```
5. Open the local URL shown in the terminal (usually `http://127.0.0.1:5173`).

If Windows says `npm is not recognized`, install the Node.js LTS version from https://nodejs.org/en, then close and reopen VS Code and check `node -v` and `npm -v`.

## Demo logins
- Customer: `demo@fincore.test` / `Demo@1234`
- Admin: `admin@fincore.test` / `Admin@1234`

Choose the matching portal on the sign-in screen.

## Folder structure
```text
fincore-nexus-project/
├── index.html              # Exact original uploaded UI/application
├── original-prototype.html # Unmodified copy of the original HTML
├── package.json            # Vite run/build scripts
├── package-lock.json       # Created after npm install (optional)
├── .gitignore
└── README.md
```

## Notes
- The UI is intentionally kept in one HTML file to preserve it exactly rather than refactor or redesign it.
- Demo data resets when the page is refreshed because the original application stores it in memory.
- This is a frontend simulation, not a production-ready banking system.
