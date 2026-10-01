/* Use a locally installed Playwright. No downloads; supports offline file:// viewer. */
const {chromium}=require('playwright');
const path=require('path');const fs=require('fs');const {pathToFileURL}=require('url');const assert=require('assert/strict');
(async()=>{const root=path.resolve(__dirname,'..'),out=path.join(root,'reports/test-artifacts');fs.mkdirSync(out,{recursive:true});
 const options={headless:true};if(process.env.PHASE1_BROWSER)options.executablePath=process.env.PHASE1_BROWSER;
 const browser=await chromium.launch(options);const page=await browser.newPage({viewport:{width:1440,height:1050},deviceScaleFactor:1});const errors=[];page.on('pageerror',e=>errors.push(String(e)));page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
 try{await page.goto(pathToFileURL(path.join(root,'reports/map-viewer/index.html')).href);await page.waitForFunction(()=>window.viewerState?.mapId===1);assert.equal(await page.locator('.map-option').count(),259);
  await page.locator('[data-record="map_npc:1"]').click();assert.match(await page.locator('#details').innerText(),/dialogue ID: 7/);
  const before=await page.locator('#map-canvas').screenshot();await page.locator('#collision').check();const after=await page.locator('#map-canvas').screenshot();assert(!before.equals(after));await page.locator('#collision').uncheck();
  await page.locator('[data-record="map_warp_point:1"]').click();assert.match(await page.locator('#details').innerText(),/目标 map 256/);await page.locator('.jump-target').click();await page.waitForFunction(()=>window.viewerState?.mapId===256);
  await page.locator('#search').fill('259');assert.equal(await page.locator('.map-option').count(),1);await page.locator('[data-map-id="259"]').click();await page.waitForFunction(()=>window.viewerState?.mapId===259);assert.match(await page.locator('#map-warning').innerText(),/NON_ORIGINAL_REFERENCE_CONTENT/);await page.locator('#search').fill('');
  await page.locator('[data-map-id="13"]').click();await page.waitForFunction(()=>window.viewerState?.mapId===13);await page.locator('[data-record="auto_move:1"]').click();assert.match(await page.locator('#details').innerText(),/auto_move #1/);
  await page.locator('[data-map-id="30"]').click();await page.waitForFunction(()=>window.viewerState?.mapId===30);await page.locator('[data-record="story:66"]').click();assert.match(await page.locator('#details').innerText(),/传送终点/);
  await page.locator('#origin').selectOption('top');await page.locator('#origin').selectOption('bottom');
  let visited=0;for(let id=1;id<=259;id++){await page.locator(`[data-map-id="${id}"]`).click();await page.waitForFunction(id=>window.viewerState?.mapId===id,id);const warning=await page.locator('#map-warning').innerText();assert(!warning.includes('缺失图片'),`missing images map ${id}`);visited++;}
  await page.locator('[data-map-id="1"]').click();await page.waitForFunction(()=>window.viewerState?.mapId===1);await page.locator('#fit').click();await page.screenshot({path:path.join(out,'viewer-map1.png'),fullPage:true});
  const canvasPng=path.join(out,'viewer-canvas.png');await page.locator('#map-canvas').screenshot({path:canvasPng});
  // file:// images taint Canvas readback in Chrome, so inspect the actual browser screenshot.
  const probe=require('child_process').spawnSync(path.join(root,'.venv/Scripts/python.exe'),['-c',"import sys;from PIL import Image;im=Image.open(sys.argv[1]).convert('RGB');print(len(set(im.getdata())))",canvasPng],{encoding:'utf8'});assert.equal(probe.status,0,probe.stderr);const colors=Number(probe.stdout.trim());assert(colors>100,'canvas screenshot must contain textured pixels');
  assert.deepEqual(errors,[]);const result={status:'PASS',mapsVisited:visited,consoleErrors:errors,screenshotUniqueColors:colors,checks:['NPC dialogue lookup','exit target and navigation','map search','modern tag','event inspection','teleport destination','collision overlay changes canvas','Y origin toggle','all 259 maps load images','screenshot pixel diversity','screenshot']};fs.writeFileSync(path.join(root,'reports/viewer-test.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
