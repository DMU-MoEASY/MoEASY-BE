// Local fake data only. Bank API contract: https://bankapi.co.kr/docs
const http = require('http');
http.createServer(async (req, res) => {
  if (!['/v1/transactions', '/v1/transactions/login'].includes(req.url)) {
    res.writeHead(501); res.end(); return;
  }
  let body = '';
  for await (const chunk of req) body += chunk;
  let input;
  try { input = JSON.parse(body); } catch { res.writeHead(400); res.end('{}'); return; }
  let status = 200;
  let output;
  const password = input.accountPassword || input.accounts?.[0]?.accountPassword;
  if (req.headers.authorization !== 'Bearer local-test-api:local-test-secret') {
    status = 401; output = { success: false, code: 'UNAUTHORIZED' };
  } else if (password === '2222') {
    status = 429; output = { success: false, code: 'ACCOUNT_COOLDOWN', retryAfterSec: 540 };
  } else if (password === '3333') {
    output = { success: false, code: 'BANK_REJECTED' };
  } else if (password === '5555') {
    status = 500; output = { success: false, code: 'CRAWLER_UNAVAILABLE' };
  } else {
    const row = { date: '2026/01/10', time: '09:00:00', type: 'deposit',
      amount: password === '4444' ? 100.5 : 100, balance: -100, description: '입금' };
    output = { success: true, transactions: [row, row] };
    if (req.url.endsWith('/login')) {
      output = { success: true, results: [{ accountNumber: input.accounts[0].accountNumber, ...output }] };
    }
  }
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(output));
}).listen(process.env.PORT || 4500);
