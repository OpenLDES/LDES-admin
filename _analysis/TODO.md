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
* [x] In a first section of the README page, document all features that are packed in this
  LDES-admin
  application. Add screenshots to this README file, and make it appealing to read. Don't make
  it too verbose. This needs to show all features of this application in a concise way, so that a
  user can quickly understand what this application can do for them.
  There's already an LDES-admin server running (http://localhost:8080/) that you can use to create
  the screenshots. Use the "demo" mode to create those screenshots. Create all screenshots in
  "black" mode.
* [x] Create full example of sensor data that is modeled in ssn-sosa ontology. Also, create a full
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
* [x] On the dashboard:
    * When you click on "LDES servers up", go to the list of LDES servers
    * When you click on "LDIOs up", go to the list of LDIOs
    * When you click on "Pipelines running", go to the list of LDIOs
    * When you click on "Members ingested", go to the list of LDES servers
* [x] When you are on the "background processes" tab of a stream, then refresh the number of
  completed, failed, average and longest recent run every 2 seconds. Make this "2 seconds"
  configurable.
* [x] Create github actions. Support the same release flow as the one for the OpenLDES server
  (See https://github.com/OpenLDES/LDESServer/tree/main/.github). Document in the README on how to
  use this flow, and which variables and passwords that you need to set in Github.
* [x] When you run a simulation in the sandbox with an HttpInPoller, then you should have the
  a button to get the data from the url that is configured, and put the response body in the
  "input member" text area. The user can then choose to change the data, and then run the
  simulation with the changed data. The user should also have the possibility to fetch the data
  constantly, and use the cron expression that was configured for the component.
* [x] Make an output adapter, `sandbox`, where you can run LDIO input, adapter, transformer and
  output components. So, this will be a local runtime engine to run separate LDIO components.
* [x] The sparql construct component in the sandbox should do a sparql construct transformation, so
  the output of this component should be the transformation of the input with the
  `Ldio:SparqlConstructTransformer` transformer. Make use of this component to do the transformation
* [x] When you change the content type of the input member in the sandbox environment, the member
  should be converted to the selected content type. For example, when you have a member in turtle
  format, and you change the content type to json-ld, the member should be available in json-ld
* [x] You should be able to change the content type of the output member in the sandbox environment
  after running a test and selecting a step.
* [x] When you run a test in the sandbox environment, and you select a component in the canvas, then
  the step of the selected component should be selected in the "steps" panel. You leave the steps as
  long as the user doesn't run or resets the simulation
* [x] the result of the json to json-ld adapter should be json-ld in the sandbox environment (also
  in
  demo mode). Now, when I run the
* [x] Change the sandbox screen, so that you can change the size of the side panels (the panel with
  components, the panel on the right with the configuration of the selected item, and the panel of
  the simulation). You should be able to make them bigger and smaller, and even to hide and expand
  them with a small button. The right configuration panel can grow to 66% of the screen width.
* [x] Make the sandbox runtime instantiate and use real LDIO components with their corresponding
  auto-configuration classes, rather than recreating their behaviour in the sandbox. Cover every
  input, adapter, transformer, and output currently supported by the sandbox runtime.
    * Inputs:
        * [x] `Ldio:HttpInPoller`
        * [x] `Ldio:HttpIn`
        * [x] `Ldio:AmqpIn`
        * [x] `Ldio:KafkaIn`
        * [x] `Ldio:LdesClient`
        * [x] `Ldio:LdesClientConnector`
    * Adapters:
        * [x] `Ldio:RdfAdapter`
        * [x] `Ldio:JsonToLdAdapter`
        * [x] `Ldio:RmlAdapter`
        * [x] `Ldio:NgsiV2ToLdAdapter`
    * Transformers:
        * [x] `Ldio:VersionObjectCreator`
        * [x] `Ldio:VersionMaterialiser`
        * [x] `Ldio:SparqlConstructTransformer`
        * [x] `Ldio:GeoJsonToWktTransformer`
        * [x] `Ldio:SkolemisationTransformer`
        * [x] `Ldio:ChangeDetectionFilter`
        * [x] `Ldio:HttpEnricher`
    * Outputs:
        * [x] `Ldio:ConsoleOut`
        * [x] `Ldio:HttpOut`
        * [x] `Ldio:HttpSparqlOut`
        * [x] `Ldio:RepositorySink`
        * [x] `Ldio:KafkaOut`
        * [x] `Ldio:AmqpOut`
        * [x] `Ldio:LdioRdbOut`
        * [x] `Ldio:NoopOut`
* [ ] Separate the different adapters in different maven modules. Add the dependencies between the
  modules in the archunit test. Make sure that you end up with a clean architecture, according to
  the hexagonal architecture pattern.
