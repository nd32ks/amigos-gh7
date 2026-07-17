import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        cream: "#F6F1E7",
        espresso: "#231A12",
        terracotta: "#C05A2E",
        sage: "#7C8B6F",
        sand: "#E4D9C3",
        amber: "#B7791F",
        brick: "#8C2F1B",
        "brick-bg": "#F6E7E2",
        "sage-bg": "#EEF1EA",
        "amber-bg": "#F7EFDD",
      },
      fontFamily: {
        display: ["var(--font-fraunces)", "Georgia", "serif"],
        ui: ["var(--font-inter)", "ui-sans-serif", "system-ui", "sans-serif"],
      },
      borderRadius: {
        card: "20px",
        "lg-card": "24px",
        pill: "9999px",
      },
      boxShadow: {
        subtle:
          "rgba(0,0,0,.4) 0 0 1px 0, rgba(0,0,0,.04) 0 1px 1px 0, rgba(0,0,0,.04) 0 2px 4px 0",
      },
    },
  },
  plugins: [],
};
export default config;
