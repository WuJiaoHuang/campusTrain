"""Generate the five JMeter 5.6.3 CLI plans using the Stage10 timer pattern."""

from pathlib import Path
import xml.etree.ElementTree as xml

HERE = Path(__file__).resolve().parent
URL = "${__groovy(new java.net.URI(props.get('BASE_URL') ?: 'http://192.168.10.102')"
AUTH = "Bearer ${__groovy(props.get('TOKEN') ?: System.getenv('BENCHMARK_TOKEN') ?: '',)}"
USERNAME = "${__groovy(props.get('USERNAME') ?: System.getenv('BENCHMARK_USERNAME') ?: '',)}"
PASSWORD = "${__groovy(props.get('PASSWORD') ?: System.getenv('BENCHMARK_PASSWORD') ?: '',)}"
LEARNING_COUNTER_SCRIPT = """
def counters = props.get('LEARN_COUNTERS')
if (counters == null) {
    counters = new java.util.concurrent.ConcurrentHashMap()
    props.put('LEARN_COUNTERS', counters)
}
def key = vars.get('username')
if (key == null) {
    key = vars.get('token')
}
def counter = counters.get(key)
if (counter == null) {
    def created = new java.util.concurrent.atomic.AtomicInteger(0)
    def existing = counters.putIfAbsent(key, created)
    counter = existing == null ? created : existing
}
vars.put('PROGRESS_SECONDS', String.valueOf(counter.incrementAndGet()))
"""


def prop(parent, name, value, kind="stringProp"):
    element = xml.SubElement(parent, kind, {"name": name})
    element.text = str(value)
    return element


def request_arguments(sampler, body):
    arguments = xml.SubElement(
        sampler,
        "elementProp",
        {"name": "HTTPsampler.Arguments", "elementType": "Arguments"},
    )
    collection = xml.SubElement(arguments, "collectionProp", {"name": "Arguments.arguments"})
    argument = xml.SubElement(collection, "elementProp", {"name": "", "elementType": "HTTPArgument"})
    prop(argument, "HTTPArgument.always_encode", "false", "boolProp")
    prop(argument, "Argument.value", body)
    prop(argument, "Argument.metadata", "=")


def assertion(parent, label, field, expected, test_type):
    element = xml.SubElement(parent, "ResponseAssertion", {
        "guiclass": "AssertionGui", "testclass": "ResponseAssertion", "testname": label
    })
    collection = xml.SubElement(element, "collectionProp", {"name": "Asserion.test_strings"})
    prop(collection, "expected", expected)
    prop(element, "Assertion.test_field", field)
    prop(element, "Assertion.assume_success", "true", "boolProp")
    prop(element, "Assertion.test_type", test_type, "intProp")
    xml.SubElement(parent, "hashTree")


def add_group(tree, label, method, path, body, token, marker, token_file=False, learning_counter=False):
    group = xml.SubElement(tree, "ThreadGroup", {
        "guiclass": "ThreadGroupGui", "testclass": "ThreadGroup", "testname": label
    })
    prop(group, "ThreadGroup.on_sample_error", "continue")
    main_controller = xml.SubElement(group, "elementProp", {
        "name": "ThreadGroup.main_controller", "elementType": "LoopController",
        "guiclass": "LoopControlPanel", "testclass": "LoopController"
    })
    prop(main_controller, "LoopController.continue_forever", "false", "boolProp")
    prop(main_controller, "LoopController.loops", "-1")
    prop(group, "ThreadGroup.num_threads", "${__P(THREADS,2)}")
    prop(group, "ThreadGroup.ramp_time", "${__P(RAMP_UP,2)}")
    prop(group, "ThreadGroup.scheduler", "true", "boolProp")
    prop(group, "ThreadGroup.duration", "${__P(DURATION,10)}")
    prop(group, "ThreadGroup.delay", "0")
    children = xml.SubElement(tree, "hashTree")

    headers = xml.SubElement(children, "HeaderManager", {
        "guiclass": "HeaderPanel", "testclass": "HeaderManager", "testname": "Request headers"
    })
    header_collection = xml.SubElement(headers, "collectionProp", {"name": "HeaderManager.headers"})
    token_header = "Bearer ${token}" if token_file else AUTH
    for header_name, header_value in [
        ("Content-Type", "application/json"),
        *([("Authorization", token_header)] if token else []),
    ]:
        header = xml.SubElement(header_collection, "elementProp", {
            "name": header_name, "elementType": "Header"
        })
        prop(header, "Header.name", header_name)
        prop(header, "Header.value", header_value)
    xml.SubElement(children, "hashTree")

    if token_file:
        csv_data = xml.SubElement(children, "CSVDataSet", {
            "guiclass": "TestBeanGUI", "testclass": "CSVDataSet", "testname": "Benchmark token pool"
        })
        prop(csv_data, "filename", "${__P(TOKEN_FILE,)}")
        prop(csv_data, "variableNames", "username,token")
        prop(csv_data, "ignoreFirstLine", "true", "boolProp")
        prop(csv_data, "recycle", "true", "boolProp")
        prop(csv_data, "delimiter", ",")
        xml.SubElement(children, "hashTree")

    if learning_counter:
        preprocessor = xml.SubElement(children, "JSR223PreProcessor", {
            "guiclass": "TestBeanGUI", "testclass": "JSR223PreProcessor",
            "testname": "Monotonic progress per benchmark user"
        })
        prop(preprocessor, "scriptLanguage", "groovy")
        prop(preprocessor, "script", LEARNING_COUNTER_SCRIPT)
        xml.SubElement(children, "hashTree")

    timer = xml.SubElement(children, "ConstantThroughputTimer", {
        "guiclass": "TestBeanGUI", "testclass": "ConstantThroughputTimer",
        "testname": "Target requests per minute"
    })
    prop(timer, "calcMode", "1", "intProp")
    prop(timer, "throughput", "${__groovy((props.get('TARGET_QPS') ?: '1').toDouble() * 60,)}")
    xml.SubElement(children, "hashTree")

    sampler = xml.SubElement(children, "HTTPSamplerProxy", {
        "guiclass": "HttpTestSampleGui", "testclass": "HTTPSamplerProxy", "testname": label
    })
    prop(sampler, "HTTPSampler.postBodyRaw", "true", "boolProp")
    request_arguments(sampler, body)
    prop(sampler, "HTTPSampler.domain", URL + ".host,)}")
    prop(sampler, "HTTPSampler.port", "${__groovy(def u=new java.net.URI(props.get('BASE_URL') ?: 'http://192.168.10.102'); u.port>0?u.port:(u.scheme=='https'?443:80),)}")
    prop(sampler, "HTTPSampler.protocol", URL + ".scheme,)}")
    prop(sampler, "HTTPSampler.path", path)
    prop(sampler, "HTTPSampler.method", method)
    prop(sampler, "HTTPSampler.follow_redirects", "false", "boolProp")
    prop(sampler, "HTTPSampler.use_keepalive", "true", "boolProp")
    prop(sampler, "HTTPSampler.connect_timeout", "5000")
    prop(sampler, "HTTPSampler.response_timeout", "30000")
    assertions = xml.SubElement(children, "hashTree")
    assertion(assertions, "HTTP 200", "Assertion.response_code", "200", "8")
    assertion(assertions, "Success body", "Assertion.response_data", marker, "2")


def write_plan(filename, groups):
    root = xml.Element("jmeterTestPlan", {
        "version": "1.2", "properties": "5.0", "jmeter": "5.6.3"
    })
    tree = xml.SubElement(root, "hashTree")
    plan = xml.SubElement(tree, "TestPlan", {
        "guiclass": "TestPlanGui", "testclass": "TestPlan",
        "testname": "CampusTrain Sentinel benchmark " + filename
    })
    prop(plan, "TestPlan.functional_mode", "false", "boolProp")
    prop(plan, "TestPlan.serialize_threadgroups", "false", "boolProp")
    arguments = xml.SubElement(plan, "elementProp", {
        "name": "TestPlan.user_defined_variables", "elementType": "Arguments",
        "guiclass": "ArgumentsPanel", "testclass": "Arguments", "testname": "Variables"
    })
    xml.SubElement(arguments, "collectionProp", {"name": "Arguments.arguments"})
    groups_tree = xml.SubElement(tree, "hashTree")
    for group in groups:
        options = group[6] if len(group) > 6 else {}
        add_group(groups_tree, *group[:6], **options)
    xml.indent(root, space="  ")
    xml.ElementTree(root).write(HERE / filename, encoding="utf-8", xml_declaration=True)


SUCCESS = r'"code"\s*:\s*200'
write_plan(
    "auth-login.jmx",
    [("auth-login", "POST", "/auth/login",
      '{"username":"' + USERNAME + '","password":"' + PASSWORD + '"}',
      False, r'"token"\s*:')],
)
write_plan(
    "learning-progress.jmx",
    [("learning-progress-report", "POST", "/learning/report-progress/${__P(LESSON_ID,199001)}",
      '{"progressSeconds":${__P(PROGRESS_SECONDS,10)}}', True, SUCCESS)],
)
for action in ("like", "unlike"):
    write_plan(
        f"community-{action}.jmx",
        [(f"community-{action}", "POST", f"/community/{action}",
          '{"targetType":"${__P(TARGET_TYPE,QUESTION)}","targetId":${__P(TARGET_ID,199000001)}}',
          True, SUCCESS)],
    )
write_plan(
    "oss-url.jmx",
    [
        ("oss-upload-url", "POST", "/video/create/upload-url",
         '{"fileName":"benchmark-smoke.mp4","contentType":"video/mp4","size":4321}',
         True, SUCCESS),
        ("oss-play-url", "GET", "/lesson/get/play-url/${__P(LESSON_ID,199001)}",
         "", True, SUCCESS),
    ],
)
write_plan(
    "learning-progress-multi.jmx",
    [("learning-progress-report", "POST", "/learning/report-progress/${__P(LESSON_ID,199001)}",
      '{"progressSeconds":${PROGRESS_SECONDS}}', True, SUCCESS,
      {"token_file": True, "learning_counter": True})],
)
for action in ("like", "unlike"):
    write_plan(
        f"community-{action}-multi.jmx",
        [(f"community-{action}", "POST", f"/community/{action}",
          '{"targetType":"${__P(TARGET_TYPE,QUESTION)}","targetId":${__P(TARGET_ID,199000001)}}',
          True, SUCCESS, {"token_file": True})],
    )
write_plan(
    "oss-upload-url.jmx",
    [("oss-upload-url", "POST", "/video/create/upload-url",
      '{"fileName":"benchmark-capacity.mp4","contentType":"video/mp4","size":4321}',
      True, SUCCESS)],
)
write_plan(
    "oss-play-url.jmx",
    [("oss-play-url", "GET", "/lesson/get/play-url/${__P(LESSON_ID,199001)}",
      "", True, SUCCESS)],
)
