// Only used by Jest, to transform the ESM-only packages in node_modules
module.exports = {
  presets: [['@babel/preset-env', { targets: { node: 'current' } }]],
}
