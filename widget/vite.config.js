import { defineConfig } from 'vite';

export default defineConfig({
  build: {
    lib: {
      entry: 'src/pandac-chat.js',
      name: 'PandacChat',
      fileName: () => 'pandac-chat.min.js',
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
});
