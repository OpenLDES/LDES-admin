# TODO

* [x] Creation of streams and views: I want that the configuration of the stream/view
  shows the properties that are configurable for that stream/view. You can change them not only by
  changing the
  yaml, but also with text inputs (when it's freetext), dropdowns (when there's a limited list to
  configure), ... The configuration that is made with those input fields is validated, and is
  reflected in the yaml (and vice-versa).
  So, for example, for the time based view, you can config the maxGranularity. You will have a
  dropdown list with possible values.
* [x] Bugfix: you cannot have multiple fragmentations per view. So, when you don't have "+ Add
  time-based", "+ Add geospatial" and "+ Add reference" under "fragmentation" in the "create new
  view" popup.
* [x] Bugfix: when you create a new "Pagination" or "Recent" view, you can now add a
  "Fragmentation".
  However, it should be impossible to add fragmentations when creating those kind of views.
* [x] In a first section of the README page, document all features that are packed in this LDES-admin
  application. Add screenshots to this README file, and make it appealing to read. Don't make
  it too verbose. This needs to show all features of this application in a concise way, so that a
  user can quickly understand what this application can do for them.
  There's already an LDES-admin server running (http://localhost:8080/) that you can use to create
  the screenshots. Use the "demo" mode to create those screenshots. Create all screenshots in
  "black" mode.
* [] Create full example of sensor data that is modeled in ssn-sosa ontology. Also, create a full
  example for parking data, based on the onboarding example
  (https://github.com/OpenLDES/Onboarding-Example/blob/main/advanced-conversion/README.md). When you
  create a new server and/or LDIO, then the user should be able to select
  "Create sensor demo stream, views, data, and pipeline" and/or "Create parking demo stream, views,
  and pipeline". This will add the stream, views (by-page and by-time), sample data for the sensor
  demo data (the parking data will be fetched from
  the parking endpoint from the city of Ghent, so it is not possible ), and LDIO pipeline (s) to
  ingest the data into the LDES server that is newly created. The selection for the examples should
  be unselected by default.
  Also, when creating a new stream, then the user should have the option to select one
  of those examples, and the configuration will be filled into the page for the creation of the new
  stream. When selecting one of the examples in the stream creation screen, the user should have the
  option to create the views, pipelines, and data (the option to create the data is only valid for
  the sensor example stream).
  When the user wants to ingest a member on one of the example streams, then example data should be
  available directly in the editor in the right format.
* [] On the dashboard:
    * When you click on "LDES servers up", go to the list of LDES servers
    * When you click on "LDIOs up", go to the list of LDIOs
    * When you click on "Pipelines running", go to the list of LDIOs
    * When you click on "Members ingested", go to the list of LDES servers
* [] When you are on the "background processes" tab of a stream, then refresh the number of
  completed, failed, average and longest recent run every 2 seconds. Make this "2 seconds"
  configurable.
* [] Create github actions. Support the same release flow as the one for the OpenLDES server
  (See https://github.com/OpenLDES/LDESServer/tree/main/.github). Document in the README on how to
  use this flow, and which variables and passwords that you need to set in Github.
