# TODO

* [x] Creation of streams and views: I want that the configuration of the stream/view
  shows the properties that are configurable for that stream/view. You can change them not only by
  changing the
  yaml, but also with text inputs (when it's freetext), dropdowns (when there's a limited list to
  configure), ... The configuration that is made with those input fields is validated, and is
  reflected in the yaml (and vice-versa).
  So, for example, for the time based view, you can config the maxGranularity. You will have a
  dropdown list with possible values.
* [] Create full example of sensor data that is modelled in ssn-sosa ontology. This can be used in
  the demo, and is directly configured on a new sandbox LDES environment: add the stream, views
  (by-page and by-time), and LDIO pipeline to ingest the data into the sandbox LDES server.
* [] Bugfix: you cannot have multiple fragmentations per view. So, when you don't have "+ Add time-based", "+ Add geospatial" and "+ Add reference" under "fragmentation" in the "create new view" popup.
* 
