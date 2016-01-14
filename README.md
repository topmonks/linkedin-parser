# linked-inparser

[![Build Status](https://travis-ci.com/topmonks/linkedin-parser.svg?token=fNQseAsgANMEusR8xYWV)](https://travis-ci.com/topmonks/linkedin-parser)

A Clojure web service to convert LinkedIn PDF profile to requested data format.

## Usage

```
curl -v -F file=@profile.pdf http://localhost:5000/linkedin
```

Response `Content-Type` is determined by `Accept` header.

[Supported formats](https://github.com/ngrunwald/ring-middleware-format)

## License

Copyright © 2016 TopMonks s.r.o.
