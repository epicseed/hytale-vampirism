#!/usr/bin/env node
// Usage: node tools/downscale.js <input> <output> <width> <height>
// Example: node tools/downscale.js src/.../Foo@2x.png src/.../Foo.png 53 53

const [,, input, output, w, h] = process.argv;

if (!input || !output || !w || !h) {
    console.error("Usage: node tools/downscale.js <input> <output> <width> <height>");
    process.exit(1);
}

const Jimp = require("jimp");

Jimp.read(input)
    .then(img => img.resize(parseInt(w), parseInt(h)).writeAsync(output))
    .then(() => console.log(`✔ ${input} → ${output} (${w}×${h})`))
    .catch(err => { console.error(err.message); process.exit(1); });
