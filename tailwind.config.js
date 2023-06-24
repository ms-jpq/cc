/** @type {import('tailwindcss').Config} */
export default {
  content: ["css/**/*.scss", "clj/**/*.cljs"],
  plugins: [],
  theme: {
    extend: {
      columns: Object.fromEntries(
        [...new Array(100)].map((_, i) => [i, `${i}`])
      ),
    },
  },
  safelist: [{ pattern: /^columns-\d+$/ }],
};
