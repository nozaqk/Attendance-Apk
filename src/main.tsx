import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "./shared/auth";
import App from "./App";
import "./shared/styles.css";
import "./app.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode><BrowserRouter><AuthProvider role="LECTURER"><App /></AuthProvider></BrowserRouter></StrictMode>,
);
