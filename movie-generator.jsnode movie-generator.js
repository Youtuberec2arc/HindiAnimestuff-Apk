const fs = require('fs');
const { MOVIES_DATA } = require('./movies-data.js');

const template = fs.readFileSync('movie-details.html', 'utf8');

MOVIES_DATA.forEach(movie => {
    const fileName = `movies/${movie.id}.html`;
    const content = template.replace(
        '<script src="movies-data.js"></script>',
        `<script>const item = ${JSON.stringify(movie)};</script>`
    );
    fs.writeFileSync(fileName, content);
    console.log('Generated: ' + fileName);
});

