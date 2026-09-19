import { defineConfig } from 'vite';

export default defineConfig(({ command }) => {
  // Dev mode: serve index.html normally (no library config needed)
  if (command === 'serve') {
    return {
      server: {
        port: 5173,
        open: true,
      },
    };
  }

  // Build mode: produce a single IIFE bundle
  return {
    build: {
      lib: {
        entry: 'src/poptalk.js',
        name: 'PopTalk',
        fileName: () => 'poptalk.min.js',
        formats: ['iife'],
      },
      outDir: 'dist',
      cssCodeSplit: false,
      rollupOptions: {
        external: [],
        output: {
          inlineDynamicImports: true,
        },
      },
      minify: 'terser',
    },
  };
});
