## Usage

The riemann crate provides a `server-spec` function that returns a
server-spec. This server spec will install and run the riemann server (not the
dashboard).  You pass a map of options to configure riemann.  The `:config`
value should be a form that will be output as the
[riemann configuration](http://riemann.io/howto.html).

The `server-spec` provides an easy way of using the crate functions, and you can
use the following crate functions directly if you need to.

The `settings` function provides a plan function that should be called in the
`:settings` phase.  The function puts the configuration options into the pallet
session, where they can be found by the other crate functions, or by other
crates wanting to interact with the riemann server.

The `install` function is responsible for actually installing riemann.  At
present installation from tarball url is the only supported method.
Installation from deb or rpm url would be nice to add, as these are now
available from the riemann site.

The `config` function writes the riemann configuration file, using the form
passed to the :config key in the `settings` function.

The `run` function starts the riemann server.
