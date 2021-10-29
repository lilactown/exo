# Ideas

`use-local-clone` a hook to initialize some local state on mount with the result
of a query, returning a tuple of the continued result of the query on the local
state and a function to change more data. essentially cloning the data cache for
that result and continuing to query it as local changes are made.
this is possible because of autonormal ❤️

swappable caching policy to allow for skipping normalization, using document
strategy (see urql)

local state

cache eviction

devtools

batching

focus refetching. how do I make this pluggable?

persisted queries, i.e. sending hashes instead of queries. needs server-side
support.

"fragments" or "slices" and hiding results to avoid data masking
