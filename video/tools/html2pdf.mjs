// Converte um HTML local em PDF A4. Uso: node tools/html2pdf.mjs entrada.html saida.pdf
import { chromium } from "playwright";
import { pathToFileURL } from "node:url";

const [, , input, output] = process.argv;
const browser = await chromium.launch();
const page = await browser.newPage();
await page.goto(pathToFileURL(input).href);
const footer = process.argv[4] || "";
await page.pdf({
  path: output, format: "A4", printBackground: true, preferCSSPageSize: true,
  displayHeaderFooter: !!footer, headerTemplate: "<span></span>",
  footerTemplate: `<div style="width:100%;text-align:center;font:7.5pt Arial;color:#777">${footer} · página <span class="pageNumber"></span> de <span class="totalPages"></span></div>`,
});
await browser.close();
console.log("pdf ok:", output);
