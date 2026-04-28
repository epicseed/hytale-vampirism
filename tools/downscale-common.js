#!/usr/bin/env node
// Finds all *@2x.png files inside Vampirism/Common, downscales them to half
// resolution, saves without the @2x suffix, and removes the @2x originals.
//
// Usage: node tools/downscale-common.js [--dry-run]

const path = require("path");
const fs = require("fs");
const Jimp = require("jimp");

const DRY_RUN = process.argv.includes("--dry-run");
const ROOT = path.resolve(__dirname, "../src/main/resources/Common/UI/Custom/Vampirism/Common");

function findAt2x(dir) {
    const results = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) results.push(...findAt2x(full));
        else if (entry.isFile() && entry.name.endsWith("@2x.png")) results.push(full);
    }
    return results;
}

(async () => {
    if (!fs.existsSync(ROOT)) {
        console.error(`Directory not found: ${ROOT}`);
        process.exit(1);
    }

    const files = findAt2x(ROOT);
    if (files.length === 0) {
        console.log("No @2x.png files found.");
        return;
    }

    console.log(`Found ${files.length} @2x file(s)${DRY_RUN ? " [dry-run]" : ""}:\n`);

    for (const src of files) {
        const dest = src.replace("@2x.png", ".png");
        const img = await Jimp.read(src);
        const w = Math.round(img.getWidth() / 2);
        const h = Math.round(img.getHeight() / 2);
        const rel = path.relative(ROOT, src);

        console.log(`  ${rel}  (${img.getWidth()}×${img.getHeight()}) → ${w}×${h}  →  ${path.basename(dest)}`);

        if (!DRY_RUN) {
            await img.resize(w, h).writeAsync(dest);
            fs.unlinkSync(src);
        }
    }

    console.log(DRY_RUN ? "\n[dry-run] No files were changed." : "\nDone.");
})().catch(err => { console.error(err.message); process.exit(1); });
