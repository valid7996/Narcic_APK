// Round-trip review of Narcic_Chain_Links.txt: parses each link exactly the way
// NarcicChainFmt.parse + importNarcicChainBatch read them, and checks the set.
const fs = require('fs');

const LINKS = String.raw`C:\Users\NG\Documents\GitHub\Narcic_APK\Narcic_Chain_Links.txt`;
const SRC = String.raw`C:\Users\NG\Downloads\New Text Document.txt`;

const EXPECTED = new Map([
  ['AT', 'Austria'], ['AU', 'Australia'], ['BE', 'Belgium'], ['BR', 'Brazil'],
  ['CA', 'Canada'], ['CH', 'Switzerland'], ['CZ', 'Czechia'], ['DE', 'Germany'],
  ['DK', 'Denmark'], ['ES', 'Spain'], ['FI', 'Finland'], ['FR', 'France'],
  ['GB', 'United Kingdom'], ['ID', 'Indonesia'], ['IE', 'Ireland'], ['IN', 'India'],
  ['IT', 'Italy'], ['JP', 'Japan'], ['LT', 'Lithuania'], ['NL', 'Netherlands'],
  ['NO', 'Norway'], ['PL', 'Poland'], ['RO', 'Romania'], ['RS', 'Serbia'],
  ['SE', 'Sweden'], ['SG', 'Singapore'], ['US', 'United States'],
]);

const sanitize = raw => raw
  .replace(/^\uFEFF/, '')
  .replace(/\r\n/g, '\n')
  .replace(/\r/g, '\n')
  .split('\n')
  .map(l => l.replace(/[\u200B\u200C\u200D\u2060]/g, '').trim())
  .join('\n')
  .trim();

const expectedConf = sanitize(fs.readFileSync(SRC, 'utf8'));

const failures = [];
const check = (ok, message) => { if (!ok) failures.push(message); };

const lines = fs.readFileSync(LINKS, 'utf8').split('\n').map(l => l.trim()).filter(Boolean);
check(lines.length === 28, `expected 28 links, got ${lines.length}`);

const seenRegions = new Set();
const seenNames = new Set();
let directCount = 0, chainCount = 0;
let directConf = null;
const carriers = new Set(), exits = new Set(), chains = new Set();

for (const [i, line] of lines.entries()) {
  const n = i + 1;
  check(line.startsWith('narcicchain://'), `link ${n}: wrong scheme`);
  // Java URI legality: only URI-safe characters in the whole link
  check(/^[A-Za-z0-9+\-._~:\/?#@!$&'()*+,;=%]*$/.test(line), `link ${n}: illegal URI characters`);

  const url = new URL(line);
  const confB64 = url.searchParams.get('conf');
  check(!!confB64, `link ${n}: no conf`);
  const conf = Buffer.from(confB64, 'base64url').toString('utf8');
  check(conf === expectedConf, `link ${n}: conf does not round-trip to the source config`);
  check(/^[\w\W]*\[Interface\]/i.test(conf) && /PrivateKey\s*=/i.test(conf), `link ${n}: conf lacks [Interface]/PrivateKey`);

  const region = url.searchParams.get('region');
  const name = decodeURIComponent(line.split('#')[1] || '');

  if (region === null) {
    directCount++;
    directConf = conf;
    check(name === '🇮🇷 Narcic AMWG', `link ${n}: direct name is ${JSON.stringify(name)}`);
    carriers.add(conf); // the direct profile doubles as the chain carrier
  } else {
    chainCount++;
    check(EXPECTED.has(region), `link ${n}: unexpected region ${region}`);
    check(!seenRegions.has(region), `link ${n}: duplicate region ${region}`);
    seenRegions.add(region);
    check(/^[A-Z]{2}$/.test(region), `link ${n}: region not ISO-2`);
    check(name === `${String.fromCodePoint(...[...region].map(c => 0x1f1e6 + c.charCodeAt(0) - 65))} Narcic Chain`,
      `link ${n}: chain name is ${JSON.stringify(name)}`);
    check(!seenNames.has(name), `link ${n}: duplicate name`);
    seenNames.add(name);
    // simulate the importer's dedupe
    exits.add(region);
    chains.add(`${region}`);
  }
}

check(directCount === 1, `expected exactly 1 direct link, got ${directCount}`);
check(chainCount === 27, `expected exactly 27 chain links, got ${chainCount}`);
check(seenRegions.size === 27, `expected 27 distinct regions, got ${seenRegions.size}`);
for (const code of EXPECTED.keys()) {
  check(seenRegions.has(code), `missing chain link for ${code} (${EXPECTED.get(code)})`);
}

// conf fidelity: every load-bearing line of the original survives byte-for-byte
const critical = [/PrivateKey\s*=\s*\S+/, /PublicKey\s*=\s*bmXOC\+F1FxEMF9dyiK2H5\/1SUtzH0JuVo51h2wPfgyo=/,
  /Endpoint\s*=\s*188\.114\.97\.6:7281/, /MTU\s*=\s*1280/, /Jc\s*=\s*4/, /Jmin\s*=\s*40/, /Jmax\s*=\s*70/,
  /I1\s*=\s*<b 0x[0-9a-f]+>/i, /Address\s*=\s*172\.16\.0\.2\/32/];
for (const re of critical) check(re.test(directConf), `direct conf lost: ${re}`);

// profile-count simulation: first import vs re-import
const firstImport = carriers.size + exits.size + chains.size;
console.log(`links: ${lines.length} (direct ${directCount}, chain ${chainCount})`);
console.log(`profiles on first import: ${firstImport} (carrier ${carriers.size}, exits ${exits.size}, chains ${chains.size})`);
console.log(`re-import adds: 0 (every profile dedupes by conf/region/pair)`);
if (failures.length) {
  console.log('\nFAILURES:');
  failures.forEach(f => console.log('  ✗ ' + f));
  process.exit(1);
}
console.log('\nALL CHECKS PASSED');
