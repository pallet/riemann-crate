[Repository](https://github.com/pallet/riemann-crate) &#xb7; [Issues](https://github.com/pallet/riemann-crate/issues)

A [pallet](http://palletops.com/) crate to install and configure
 [riemann](http://riemann.io).
### Dependency Information

```clj
:dependencies [[com.palletops/riemann-crate "0.8.0-alpha.1"]]
```

### Releases

<table>
<thead>
  <tr><th>Pallet</th><th>Crate Version</th><th>Repo</th><th>GroupId</th></tr>
</thead>
<tbody>
  <tr>
    <th>0.8.0-beta.1</th>
    <td>0.8.0-alpha.1</td>
    <td>clojars</td>
    <td>com.palletops</td>
    <td><a href='https://github.com/pallet/riemann-crate/blob/0.8.0-alpha.1/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/riemann-crate/blob/0.8.0-alpha.1/'>Source</a></td>
  </tr>
</tbody>
</table>
## Usage

The riemann crate provides a `riemann` function that returns a server-spec. This
server spec will install and run the riemann server (not the dashboard).

You pass a map of options to configure riemann. The `:config` value should be a
form that will be output as the
[riemann configuration](http://riemann.io/howto.html).
## License

Copyright (C) 2012, 2013 Hugo Duncan

Distributed under the Eclipse Public License.
