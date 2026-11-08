#!/usr/bin/env node
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { createServer } from './index.js';

if (process.argv.includes('--help') || process.argv.includes('-h')) {
  console.log('Usage: i18n-mcp\n\nStarts the i18n MCP server over stdio.');
} else {
  await createServer().connect(new StdioServerTransport());
}
