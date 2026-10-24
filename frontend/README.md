# Finance Tracker Frontend

React + TypeScript app built with Vite.

## Run locally

Requires Node.js. Start the backend on port 8080 first (the dev server proxies `/api` to it).

```sh
cd frontend
npm install
npm run dev
```

Then open the URL printed in the terminal (usually http://localhost:5173).

Other commands:

```sh
npm run build   # type-check and build to dist/
npm run lint    # run Oxlint
```

## Deployment

`vercel.json` rewrites `/api/*` to the backend on Render. Replace `REPLACE_ME` with your Render service name.
