const fs = require('fs');
const path = require('path');

// 1. data.js se data load karna
const dataContent = fs.readFileSync('data.js', 'utf8');
const dataScript = dataContent + "\nmodule.exports = ANIME_DATA;";
const ANIME_DATA = eval(dataScript);

// 2. details.html ko template ki tarah read karna
const templateHtml = fs.readFileSync('details.html', 'utf8');

// Output folder setup
const outputDir = path.join(__dirname, 'anime-pages');
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir);
}

console.log("🚀 Mobile + PC Fully Responsive HTML Generation Shuru Ho Raha Hai...\n");

ANIME_DATA.forEach((item) => {
    const siblings = ANIME_DATA.filter(s => s.title === item.title);
    const newButtonText = `Download ${item.quality || 'HD'} →`;

    // Modern Responsive Design and Script Content
    const dynamicContent = `
  <main class="anime-container">
    <div class="anime-layout">
      <div class="anime-poster-wrapper">
        <img class="anime-poster" src="${item.thumb}" alt="${item.title}">
      </div>
      <div class="anime-details">
        <h1 class="anime-title">${item.title}</h1>
        <div class="anime-meta">
          <span class="meta-badge season">${item.season}</span>
          <span class="meta-badge rating">★ ${item.rating || '?'}</span>
        </div>
        <p class="anime-synopsis">${item.synopsis || 'No synopsis available.'}</p>
        
        <div class="anime-specs">
          <div class="spec"><div class="spec-label">Format</div><div class="spec-value">${item.run || '—'}</div></div>
          <div class="spec"><div class="spec-label">Duration</div><div class="spec-value">${item.runtime || '—'}</div></div>
          <div class="spec"><div class="spec-label">Episodes</div><div class="spec-value">${item.episodes ?? '—'}</div></div>
          <div class="spec"><div class="spec-label">Language</div><div class="spec-value">${item.language || '—'}</div></div>
          <div class="spec"><div class="spec-label">Subtitle</div><div class="spec-value">${item.subtitle || '—'}</div></div>
          <div class="spec"><div class="spec-label">Quality</div><div class="spec-value">${item.quality || '—'}</div></div>
        </div>
        <a class="watch-btn" href="${item.platformUrl}" target="_blank" rel="noopener">${newButtonText}</a>
        <div class="platform-note">Opens the official ${item.platform || 'source'} page in a new tab.</div>

        ${siblings.length > 1 ? `
        <div class="more-seasons">
          <div class="more-seasons-title">More from this series</div>
          <div class="season-pills">
            ${siblings.map(s => `<a class="season-pill ${s.id === item.id ? 'current' : ''}" href="${s.id}.html">${s.season}</a>`).join('')}
          </div>
        </div>` : ''}
      </div>
    </div>
  </main>

  <script>
    const item = ${JSON.stringify(item)};
    document.getElementById('shareBtn').addEventListener('click', function() {
      if(!item) return;
      const shareText = \`✦ \${item.title} \${item.season}\\n\` +
    \`➥  Rating: \${item.rating}/10\\n\` +
    \`➥  Genres: \${(item.genres || []).join(', ')}\\n\` +
    \`➥  Episodes: \${item.episodes ?? '—'}\\n\` +
    \`➥  Language: \${item.language}\\n\` +
    \`➥  Quality: \${item.quality}\\n\\n\` +
    \`➥  Watch Now: \${window.location.href}\`;

      if(navigator.share) {
        navigator.share({
          title: \`\${item.title} \${item.season}\`,
          text: shareText,
          url: window.location.href
        }).catch(() => {});
      } else {
        navigator.clipboard.writeText(shareText).then(() => {
          alert('Share info copied to clipboard!');
        }).catch(() => {});
      }
    });
  </script>
    `;

    let finalHtml = templateHtml;

    // 1. Dynamic Favicon Inject Karna (Purana favicon link hatakar ya head tag ke end mein thumbnail add karna)
    const faviconTag = `<link rel="icon" type="image/jpeg" href="${item.thumb}">`;
    if (finalHtml.includes('</head>')) {
        finalHtml = finalHtml.replace('</head>', `${faviconTag}\n</head>`);
    }

    // 2. Head ka title tag update karna
    finalHtml = finalHtml.replace(/<title>[\s\S]*<\/title>/, `<title>${item.title} - ${item.season} — HindiAnimeStuff</title>`);
    
    // 3. Pure <main> element aur custom dynamic logic ko replace karna
    const mainRegex = /<main[\s\S]*<\/script>\s*<\/body>/;
    finalHtml = finalHtml.replace(mainRegex, `${dynamicContent}\n</body>`);

    // 4. Mobile Responsiveness and Clean Layout CSS Inject (Media queries add ki gayi hain)
    const responsiveStyle = `
<style>
  /* Base Layout Mobile-First Fixes */
  .anime-layout {
    display: flex;
    flex-direction: column;
    gap: 24px;
    padding: 16px;
    max-width: 1200px;
    margin: 0 auto;
  }
  .anime-poster-wrapper {
    width: 100%;
    max-width: 320px;
    margin: 0 auto;
    flex-shrink: 0;
  }
  .anime-poster {
    width: 100%;
    height: auto;
    border-radius: 12px;
    object-fit: cover;
    box-shadow: 0 8px 24px rgba(0,0,0,0.5);
  }
  .anime-details {
    width: 100%;
  }
  .anime-title {
    font-size: 24px;
    line-height: 1.3;
    margin-bottom: 12px;
    word-wrap: break-word;
  }
  .anime-specs {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
    gap: 12px;
    margin: 20px 0;
  }
  .spec {
    background: var(--card);
    padding: 10px 12px;
    border-radius: 8px;
    border: 1px solid rgba(255,255,255,0.05);
  }
  
  /* Desktop/Tablet Configuration */
  @media (min-width: 768px) {
    .anime-layout {
      flex-direction: row;
      align-items: flex-start;
      gap: 40px;
      padding: 32px 24px;
    }
    .anime-poster-wrapper {
      position: sticky;
      top: 24px;
      width: 300px;
    }
    .anime-title {
      font-size: 36px;
    }
  }
</style>
`;
    if (finalHtml.includes('</style>')) {
        finalHtml = finalHtml.replace('</style>', `${responsiveStyle}\n</style>`);
    }

    const fileName = `${item.id}.html`;
    fs.writeFileSync(path.join(outputDir, fileName), finalHtml, 'utf8');
    console.log(`Generated: ${fileName}`);
});

console.log(`\n🚀 Done! Total ${ANIME_DATA.length} separate responsive files generated successfully!`);
