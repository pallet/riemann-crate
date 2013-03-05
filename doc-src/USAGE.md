## Usage

The riemann crate provides a `riemann` function that returns a server-spec. This
server spec will install and run the riemann server (not the dashboard).

You pass a map of options to configure riemann. The `:config` value should be a
form that will be output as the
[riemann configuration](http://riemann.io/howto.html).
