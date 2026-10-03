// Builds the 28 Narcic Chain links from the AmneziaWG config the user provided.
// The normalization mirrors AwgManager.sanitizeConfigText so the stored profile
// text is identical whether it came from a pasted .conf or a link.
const fs = require('fs');

const SRC = String.raw`C:\Users\NG\Downloads\New Text Document.txt`;
const OUT = String.raw`C:\Users\NG\Documents\GitHub\Narcic_APK\Narcic_Chain_Links.txt`;

// The 27 exit countries of the app's picker (AETHER_EXIT_COUNTRIES), code → English label.
const COUNTRIES = [
  ['AT', 'Austria'], ['AU', 'Australia'], ['BE', 'Belgium'], ['BR', 'Brazil'],
  ['CA', 'Canada'], ['CH', 'Switzerland'], ['CZ', 'Czechia'], ['DE', 'Germany'],
  ['DK', 'Denmark'], ['ES', 'Spain'], ['FI', 'Finland'], ['FR', 'France'],
  ['GB', 'United Kingdom'], ['ID', 'Indonesia'], ['IE', 'Ireland'], ['IN', 'India'],
  ['IT', 'Italy'], ['JP', 'Japan'], ['LT', 'Lithuania'], ['NL', 'Netherlands'],
  ['NO', 'Norway'], ['PL', 'Poland'], ['RO', 'Romania'], ['RS', 'Serbia'],
  ['SE', 'Sweden'], ['SG', 'Singapore'], ['US', 'United States'],
];

const flagOf = code => String.fromCodePoint(...[...code].map(c => 0x1f1e6 + c.charCodeAt(0) - 65));

function sanitize(raw) {
  return raw
    .replace(/^\uFEFF/, '')
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .split('\n')
    .map(l => l.replace(/[\u200B\u200C\u200D\u2060]/g, '').trim())
    .join('\n')
    .trim();
}

const conf = sanitize(fs.readFileSync(SRC, 'utf8'));
if (!conf.includes('[Interface]') || !conf.includes('[Peer]') || !/PrivateKey\s*=/i.test(conf)) {
  throw new Error('the source file does not read as an AmneziaWG config');
}
const b64 = Buffer.from(conf, 'utf8').toString('base64url'); // unpadded by default

const lines = [];
// 1) the plain config, no chain — runs standalone from the امنزیا page
lines.push(`narcicchain://direct?conf=${b64}#${encodeURIComponent(flagOf('IR') + ' Narcic AMWG')}`);
// 2) one two-engine chain per Psiphon exit country: AWG carrier → Psiphon exit
for (const [code, label] of COUNTRIES) {
  const name = `${flagOf(code)} Narcic Chain`;
  lines.push(`narcicchain://chain?conf=${b64}&region=${code}#${encodeURIComponent(name)}`);
}

fs.writeFileSync(OUT, lines.join('\n') + '\n', 'utf8');
console.log(`conf: ${conf.length} chars, ${conf.split('\n').length} lines`);
console.log(`wrote ${lines.length} links to ${OUT}`);
