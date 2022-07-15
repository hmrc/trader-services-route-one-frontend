const path = require('path');
const CssMinimizerPlugin = require("css-minimizer-webpack-plugin");
const MiniCssExtractPlugin = require("mini-css-extract-plugin").default;

module.exports = function (env) {
  return {
    plugins: [new MiniCssExtractPlugin({
      filename: env.output.filename
    })],
    optimization: {
      minimizer: [`...`, new CssMinimizerPlugin()],
    },
    mode: 'production',
    watch: false,
    devtool: 'source-map',
    entry: Object.values(env.entry),
    resolve: {
      extensions: ['.sass', '.scss'],
      alias: {
        'node_modules': path.join(__dirname, 'node_modules'),
        'webjars': env.webjars.path
      }
    },
    module: {
      rules: [
        {
          test: /\.(sa|sc|c)ss$/,
          use: [
            { loader: MiniCssExtractPlugin.loader, options: {} },
            { loader: 'css-loader', options: { url: false, sourceMap: true } },
            { loader: 'sass-loader', options: { sourceMap: true } }
          ],
        },
      ],
    },
    output: {
      path: env.output.path
    },
    stats: {
      relatedAssets: true,
      moduleAssets: true,
      cachedAssets: true,
      nestedModules: true,
      runtimeModules: true,
      dependentModules: true,
      groupAssetsByPath: true,
      groupModulesByPath: true,
      children: true,
      chunks: true,
      modules: true,
      outputPath: true,
      providedExports: false,
      reasons: false,
      source: true,
      chunkGroupChildren: true,
      chunkRelations: true,
      modulesSpace: 100,
      assetsSpace: 100,
      chunkModulesSpace: 100,
      nestedModulesSpace: 100,
    }
  }
};
