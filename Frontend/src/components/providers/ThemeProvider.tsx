"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";

/**
 * Light/dark theme provider for the whole app. Dark is the product's
 * default look (see globals.css) — light mode is an opt-in the person
 * switches to via ThemeToggle, not a system-preference default, so
 * enableSystem stays off and defaultTheme stays "dark".
 *
 * attribute="class" makes next-themes add/remove a "light" or "dark"
 * class on <html>; globals.css defines the base (dark) palette on :root
 * and overrides it under :root.light.
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return (
    <NextThemesProvider attribute="class" defaultTheme="dark" enableSystem={false} disableTransitionOnChange>
      {children}
    </NextThemesProvider>
  );
}
