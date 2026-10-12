import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "./shared/auth";
import App from "./App";
import "./shared/styles.css";
import "./app.css";
import "./noselect.css";

// No long-press / right-click menu (copy, select all ...) except inside input boxes.
const inField = (n: EventTarget | null) => { const el = n instanceof Element ? n : (n as Node | null)?.parentElement; return !!el?.closest("input, textarea"); };
document.addEventListener("contextmenu", (e) => { if (!inField(e.target)) e.preventDefault(); });
document.addEventListener("selectstart", (e) => { if (!inField(e.target)) e.preventDefault(); });

createRoot(document.getElementById("root")!).render(
  <StrictMode><BrowserRouter><AuthProvider role="LECTURER"><App /></AuthProvider></BrowserRouter></StrictMode>,
);
