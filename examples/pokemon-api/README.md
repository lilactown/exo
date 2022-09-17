# Example: pokemon-api

An example app using exo, exo-hooks and pathom.

## Developing

To get started, clone this repo and navigate to this folder.

```
git clone https://github.com/lilactown/exo.git
cd exo/examples/pokemon-api
```

Then, install JS dependencies via [npm](https://nodejs.org/en/).

```
npm i
```

Finally, you can start the development server using shadow-cljs

```
npx shadow-cljs watch app
```

To do a release build, use `npx shadow-cljs release app`.

## App structure

The app uses pathom to wrap the [PokéAPI](https://pokeapi.co/). The file
[./src/demo/pokemon/api.cljs] implements the pathom resolvers and creates the
pathom environment that the app uses to fetch data.

For ease of implementing this demo, pathom is used on the client. In production
usage, you'll often implement this pathom wrapper on the backend and send your
queries over HTTP and receive the exact response that you asked for.

[./src/demo/pokemon/main.cljs] contains the code for the main app, built using
[helix](https://github.com/lilactown/helix) for writing React components and
[exo-hooks](../../exo-hooks) for caching and subscribing to queries that are
resolved via pathom.

It shows how one can structure the app, showing things conditionally, preload
queries on user action, and create a decent UX for data driven apps.
