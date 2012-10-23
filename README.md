# riemann-crate

A [pallet](http://palletops.com/) crate to install and configure
[riemann](http://aphyr.github.com/riemann/index.html).

Depends on pallet 0.8.

## Usage

Add the following to your `:dependencies`:

```clj
[org.cloudhoist/riemann-crate "0.1.0-SNAPSHOT"]
```

The riemann crate provides a `riemann` function that returns a server-spec. This
server spec will install and run the riemann server (not the dashboard).

You pass a map of options to configure riemann. The `:config` should contain a
form that will be output as the
[riemann configuration](http://aphyr.github.com/riemann/configuring.html).

## License

Copyright (C) 2012 Hugo Duncan

Distributed under the Eclipse Public License.
