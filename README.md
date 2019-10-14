# Server Pack Locator

One of a series of locator modules to allow Forge to find mods in different places.

This locator allows a properly configured client to retrieve mods from a properly configured server,
potentially allowing for custom server builds without a lot of mod distribution hassle.

## Trust

Trust is established using the well known and well documented mechanisms of *mutual SSL*.

Put simply, each side generates a secure key and gets the other to sign a secured certificate based on
that key. If both present their secured certificates, the connection is allowed, if not, the connection
is rejected.

## How the server works

The server generates a certificate (called a "Certificate Authority") and associated private key. _Do not_ share or 
delete this private key - without it, the "Certificate Authority" will not work.

It then creates a special, single purpose HTTP server using this certificate in "mutual SSL" mode. This means
that only connections coming from clients bearing certificates signed by the "Certificate Authority" will
be accepted. In addition, each client certificate's "Common Name" is checked against the current server
whitelist (by UUID). Any Common Name not found on the whitelist will be disconnected. Note that the Common Name
is cryptographically encoded by the server - it cannot be forged by the client.

Those connections that are allowed will be served a _manifest_ of current mods in the servermods directory. This
manifest is "smart filtered" so only the latest (by version) of any modid will be served. The same list will also
be offered to the server itself, ensuring overlapping mod lists on client and server.

(Note that mods in the mods dir will be loaded as well, but are not offered in this way to clients. Use this for
server-only mods)
 
## How the client works

The client generates a private key and a CSR - "Certificate Signing Request". This request encodes the UUID of the client
as launched by the game. This should be signed by the server administrator using the signing process below. In return
the server administrator will supply you with a signed certificate. This should be placed next to the signing request and
private key.

The client then configures the server connection, and next time the client starts, it should fetch the current modlist
from the server automatically - no further interaction required.   

