import { migrate } from './client.js';

migrate()
  .then(() => {
    console.log('Migrations applied.');
    process.exit(0);
  })
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
