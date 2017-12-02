package io.swagger.codegen.languages;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.CodegenModel;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenParameter;
import io.swagger.codegen.CodegenProperty;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.DefaultCodegen;
import io.swagger.codegen.SupportingFile;
import io.swagger.oas.models.OpenAPI;
import io.swagger.oas.models.info.Info;
import io.swagger.oas.models.media.ArraySchema;
import io.swagger.oas.models.media.BinarySchema;
import io.swagger.oas.models.media.BooleanSchema;
import io.swagger.oas.models.media.ByteArraySchema;
import io.swagger.oas.models.media.DateSchema;
import io.swagger.oas.models.media.DateTimeSchema;
import io.swagger.oas.models.media.EmailSchema;
import io.swagger.oas.models.media.FileSchema;
import io.swagger.oas.models.media.IntegerSchema;
import io.swagger.oas.models.media.MapSchema;
import io.swagger.oas.models.media.NumberSchema;
import io.swagger.oas.models.media.ObjectSchema;
import io.swagger.oas.models.media.PasswordSchema;
import io.swagger.oas.models.media.Schema;
import io.swagger.oas.models.media.StringSchema;
import io.swagger.oas.models.media.UUIDSchema;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.swagger.codegen.languages.helpers.ExtensionHelper.getBooleanValue;

public class ElixirClientCodegen extends DefaultCodegen implements CodegenConfig {
    protected String apiVersion = "1.0.0";
    protected String moduleName;
    protected static final String defaultModuleName = "Swagger.Client";

    // This is the name of elixir project name;
    protected static final String defaultPackageName = "swagger_client";

    String supportedElixirVersion = "1.4";
    List<String> extraApplications = Arrays.asList(":logger");
    List<String> deps = Arrays.asList(
            "{:tesla, \"~> 0.8\"}",
            "{:poison, \">= 1.0.0\"}"
    );

    public ElixirClientCodegen() {
        super();

        // set the output folder here
        outputFolder = "generated-code/elixir";

        /*
         * Models.  You can write model files using the modelTemplateFiles map.
         * if you want to create one template for file, you can do so here.
         * for multiple files for model, just put another entry in the `modelTemplateFiles` with
         * a different extension
         */
        modelTemplateFiles.put(
                "model.mustache", // the template to use
                ".ex");       // the extension for each file to write

        /**
         * Api classes.  You can write classes for each Api file with the apiTemplateFiles map.
         * as with models, add multiple entries with different extensions for multiple files per
         * class
         */
        apiTemplateFiles.put(
                "api.mustache",   // the template to use
                ".ex");       // the extension for each file to write

        /**
         * Template Location.  This is the location which templates will be read from.  The generator
         * will use the resource stream to attempt to read the templates.
         */
        templateDir = "elixir";

        /**
         * Reserved words.  Override this with reserved words specific to your language
         * Ref: https://github.com/itsgreggreg/elixir_quick_reference#reserved-words
         */
        reservedWords = new HashSet<String>(
                Arrays.asList(
                        "nil",
                        "true",
                        "false",
                        "__MODULE__",
                        "__FILE__",
                        "__DIR__",
                        "__ENV__",
                        "__CALLER__")
        );

        /**
         * Additional Properties.  These values can be passed to the templates and
         * are available in models, apis, and supporting files
         */
        additionalProperties.put("apiVersion", apiVersion);

        /**
         * Supporting Files.  You can write single files for the generator with the
         * entire object tree available.  If the input file has a suffix of `.mustache
         * it will be processed by the template engine.  Otherwise, it will be copied
         */
        supportingFiles.add(new SupportingFile("README.md.mustache",   // the input template or file
                "",                                                       // the destination folder, relative `outputFolder`
                "README.md")                                          // the output file
        );
        supportingFiles.add(new SupportingFile("config.exs.mustache",
                "config",
                "config.exs")
        );
        supportingFiles.add(new SupportingFile("mix.exs.mustache",
                "",
                "mix.exs")
        );
        supportingFiles.add(new SupportingFile("test_helper.exs.mustache",
                "test",
                "test_helper.exs")
        );
        supportingFiles.add(new SupportingFile("gitignore.mustache",
                "",
                ".gitignore")
        );

        /**
         * Language Specific Primitives.  These types will not trigger imports by
         * the client generator
         */
        languageSpecificPrimitives = new HashSet<String>(
                Arrays.asList(
                        "Integer",
                        "Float",
                        "Boolean",
                        "String",
                        "List",
                        "Atom",
                        "Map",
                        "Tuple",
                        "PID",
                        "DateTime"
                        )
        );

        // ref: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#data-types
        typeMapping = new HashMap<String, String>();
        typeMapping.put("integer", "Integer");
        typeMapping.put("long", "Integer");
        typeMapping.put("number", "Float");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Float");
        typeMapping.put("string", "String");
        typeMapping.put("byte", "Integer");
        typeMapping.put("boolean", "Boolean");
        typeMapping.put("Date", "DateTime");
        typeMapping.put("DateTime", "DateTime");
        typeMapping.put("file", "String");
        typeMapping.put("map", "Map");
        typeMapping.put("array", "List");
        typeMapping.put("list", "List");
        // typeMapping.put("object", "Map");
        typeMapping.put("binary", "String");
        typeMapping.put("ByteArray", "String");
        typeMapping.put("UUID", "String");

        cliOptions.add(new CliOption(CodegenConstants.INVOKER_PACKAGE, "The main namespace to use for all classes. e.g. Yay.Pets"));
        cliOptions.add(new CliOption("licenseHeader", "The license header to prepend to the top of all source files."));
        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_NAME, "Elixir package name (convention: lowercase)."));
    }

    /**
     * Configures the type of generator.
     *
     * @return the CodegenType for this generator
     * @see io.swagger.codegen.CodegenType
     */
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    /**
     * Configures a friendly name for the generator.  This will be used by the generator
     * to select the library with the -l flag.
     *
     * @return the friendly name for the generator
     */
    public String getName() {
        return "elixir";
    }

    /**
     * Returns human-friendly help for the generator.  Provide the consumer with help
     * tips, parameters here
     *
     * @return A string value for the help message
     */
    public String getHelp() {
        return "Generates an elixir client library (alpha).";
    }

    @Override
    public void processOpts() {
        super.processOpts();
        additionalProperties.put("supportedElixirVersion", supportedElixirVersion);
        additionalProperties.put("extraApplications", join(",", extraApplications));
        additionalProperties.put("deps", deps);
        additionalProperties.put("underscored", new Mustache.Lambda() {
            @Override
            public void execute(Template.Fragment fragment, Writer writer) throws IOException {
                writer.write(underscored(fragment.execute()));
            }
        });
        additionalProperties.put("modulized", new Mustache.Lambda() {
            @Override
            public void execute(Template.Fragment fragment, Writer writer) throws IOException {
                writer.write(modulized(fragment.execute()));
            }
        });

        if (additionalProperties.containsKey(CodegenConstants.INVOKER_PACKAGE)) {
            setModuleName((String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE));
        }
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
         Info info = openAPI.getInfo();
         if (moduleName == null) {
             if (info.getTitle() != null) {
                 // default to the appName (from title field)
                 setModuleName(modulized(escapeText(info.getTitle())));
             } else {
                 setModuleName(defaultModuleName);
             }
        }
        additionalProperties.put("moduleName", moduleName);

        if (!additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)) {
            additionalProperties.put(CodegenConstants.PACKAGE_NAME, underscored(moduleName));
        }

        supportingFiles.add(new SupportingFile("connection.ex.mustache",
            sourceFolder(),
            "connection.ex"));

        supportingFiles.add(new SupportingFile("request_builder.ex.mustache",
            sourceFolder(),
            "request_builder.ex"));


        supportingFiles.add(new SupportingFile("deserializer.ex.mustache",
            sourceFolder(),
            "deserializer.ex"));
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        Map<String, Object> operations = (Map<String, Object>) super.postProcessOperations(objs).get("operations");
        List<CodegenOperation> os = (List<CodegenOperation>) operations.get("operation");
        List<ExtendedCodegenOperation> newOs = new ArrayList<ExtendedCodegenOperation>();
        Pattern pattern = Pattern.compile("\\{([^\\}]+)\\}([^\\{]*)");
        for (CodegenOperation o : os) {
            ArrayList<String> pathTemplateNames = new ArrayList<String>();
            Matcher matcher = pattern.matcher(o.path);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String pathTemplateName = matcher.group(1);
                matcher.appendReplacement(buffer, "#{" + underscore(pathTemplateName) + "}" + "$2");
                pathTemplateNames.add(pathTemplateName);
            }
            ExtendedCodegenOperation eco = new ExtendedCodegenOperation(o);
            if (buffer.toString().isEmpty()) {
                eco.setReplacedPathName(o.path);
            } else {
                eco.setReplacedPathName(buffer.toString());
            }
            eco.setPathTemplateNames(pathTemplateNames);

            // detect multipart form types
            if (eco.hasConsumes == Boolean.TRUE) {
                Map<String, String> firstType = eco.consumes.get(0);
                if (firstType != null) {
                    if ("multipart/form-data".equals(firstType.get("mediaType"))) {
                        eco.isMultipart = Boolean.TRUE;
                    }
                }
            }

            newOs.add(eco);
        }
        operations.put("operation", newOs);
        return objs;
    }

    @Override
    public CodegenModel fromModel(String name, Schema schema, Map<String, Schema> allSchemas) {
        CodegenModel cm = super.fromModel(name, schema, allSchemas);
        return new ExtendedCodegenModel(cm);
    }

    // We should use String.join if we can use Java8
    String join(CharSequence charSequence, Iterable<String> iterable) {
        StringBuilder buf = new StringBuilder();
        for (String str : iterable) {
            if (0 < buf.length()) {
                buf.append((charSequence));
            }
            buf.append(str);
        }
        return buf.toString();
    }

    String underscored(String words) {
        ArrayList<String> underscoredWords = new ArrayList<String>();
        for (String word : words.split(" ")) {
            underscoredWords.add(underscore(word));
        }
        return join("_", underscoredWords);
    }

    String modulized(String words) {
        ArrayList<String> modulizedWords = new ArrayList<String>();
        for (String word : words.split(" ")) {
            modulizedWords.add(camelize(word));
        }
        return join("", modulizedWords);
    }

    /**
     * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
     * those terms here.  This logic is only called if a variable matches the reserved words
     *
     * @return the escaped term
     */
    @Override
    public String escapeReservedWord(String name) {
        return "_" + name;  // add an underscore to the name
    }

    private String sourceFolder() {
        ArrayList<String> underscoredWords = new ArrayList<String>();
        for (String word : moduleName.split("\\.")) {
            underscoredWords.add(underscore(word));
        }
        return "lib/" + join("/", underscoredWords);
    }

    /**
     * Location to write model files.  You can use the modelPackage() as defined when the class is
     * instantiated
     */
    public String modelFileFolder() {
        return outputFolder + "/" + sourceFolder() + "/" + "model";
    }

    /**
     * Location to write api files.  You can use the apiPackage() as defined when the class is
     * instantiated
     */
    @Override
    public String apiFileFolder() {
        return outputFolder + "/" + sourceFolder() + "/" + "api";
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "Default";
        }
        return camelize(name);
    }

    @Override
    public String toApiFilename(String name) {
        // replace - with _ e.g. created-at => created_at
        name = name.replaceAll("-", "_");

        // e.g. PetApi.go => pet_api.go
        return underscore(name);
    }

    @Override
    public String toModelName(String name) {
        // camelize the model name
        // phone_number => PhoneNumber
        return camelize(toModelFilename(name));
    }

    @Override
    public String toModelFilename(String name) {
        if (!StringUtils.isEmpty(modelNamePrefix)) {
            name = modelNamePrefix + "_" + name;
        }

        if (!StringUtils.isEmpty(modelNameSuffix)) {
            name = name + "_" + modelNameSuffix;
        }

        name = sanitizeName(name);

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(name)) {
            LOGGER.warn(name + " (reserved word) cannot be used as model name. Renamed to " + ("model_" + name));
            name = "model_" + name; // e.g. return => ModelReturn (after camelize)
        }

        // model name starts with number
        if (name.matches("^\\d.*")) {
            LOGGER.warn(name + " (model name starts with number) cannot be used as model name. Renamed to " + ("model_" + name));
            name = "model_" + name; // e.g. 200Response => Model200Response (after camelize)
        }

        return underscore(name);
    }

    @Override
    public String toOperationId(String operationId) {
        // throw exception if method name is empty (should not occur as an auto-generated method name will be used)
        if (StringUtils.isEmpty(operationId)) {
            throw new RuntimeException("Empty method name (operationId) not allowed");
        }

        return camelize(sanitizeName(operationId));
    }

    /**
     * Optional - type declaration.  This is a String which is used by the templates to instantiate your
     * types.  There is typically special handling for different property types
     *
     * @return a string value used as the `dataType` field for model templates, `returnType` for api templates
     */
    @Override
    /**
<<<<<<< HEAD
    public String getTypeDeclaration(Schema propertySchema) {
        if (propertySchema instanceof ArraySchema) {
            Schema inner = ((ArraySchema) propertySchema).getItems();
            return String.format("%s[%s]", getSchemaType(propertySchema), getTypeDeclaration(inner));
        } else if (propertySchema instanceof MapSchema) {
            Schema inner = propertySchema.getAdditionalProperties();
            return String.format("%s[String, %s]", getSchemaType(propertySchema), getTypeDeclaration(inner));
=======*/
    public String getTypeDeclaration(Schema propertySchema) {
        // SubClasses of AbstractProperty
        //
        // ArrayProperty
        // MapProperty
        // PasswordProperty
        // StringProperty
        //     EmailProperty
        //     ByteArrayProperty
        // DateProperty
        // UUIDProperty
        // DateTimeProperty
        // ObjectProperty
        // AbstractNumericProperty
        //     BaseIntegerProperty
        //         IntegerProperty
        //         LongProperty
        //     DecimalProperty
        //         DoubleProperty
        //         FloatProperty
        // BinaryProperty
        // BooleanProperty
        // RefProperty
        // FileProperty
        if (propertySchema instanceof ArraySchema) {
            ArraySchema ap = (ArraySchema) propertySchema;
            Schema inner = ap.getItems();
            return "[" + getTypeDeclaration(inner) + "]";
        } else if (propertySchema instanceof MapSchema) {
            Schema inner = propertySchema.getAdditionalProperties();
            return "%{optional(String.t) => " + getTypeDeclaration(inner) + "}";
        } else if (propertySchema instanceof PasswordSchema
                || propertySchema instanceof EmailSchema
                || propertySchema instanceof StringSchema
                || propertySchema instanceof UUIDSchema
                || propertySchema instanceof FileSchema
                ) {
            return "String.t";
        } else if (propertySchema instanceof ByteArraySchema
                || propertySchema instanceof BinarySchema) {
            return "binary()";
        } else if (propertySchema instanceof DateSchema) {
            return "Date.t";
        } else if (propertySchema instanceof DateTimeSchema) {
            return "DateTime.t";
        } else if (propertySchema instanceof ObjectSchema) {
            // How to map it?
            return super.getTypeDeclaration(propertySchema);
        } else if (propertySchema instanceof IntegerSchema) {
        } else if (propertySchema instanceof NumberSchema) {
            return "float()";
        } else if (propertySchema instanceof BooleanSchema) {
            return "boolean()";
        } else if (StringUtils.isNotBlank(propertySchema.get$ref())) {
            // How to map it?
            return super.getTypeDeclaration(propertySchema);
        }
        return super.getTypeDeclaration(propertySchema);
    }

    /**
     * Optional - swagger type conversion.  This is used to map swagger types in a `Property` into
     * either language specific types via `typeMapping` or into complex models if there is not a mapping.
     *
     * @return a string value of the type or complex model for this property
     * @see io.swagger.oas.models.media.Schema
     */
    @Override
    public String getSchemaType(Schema propertySchema) {
        String swaggerType = super.getSchemaType(propertySchema);
        String type = null;
        if (typeMapping.containsKey(swaggerType)) {
            type = typeMapping.get(swaggerType);
            if (languageSpecificPrimitives.contains(type))
                return toModelName(type);
        } else
            type = swaggerType;
        return toModelName(type);
    }

    class ExtendedCodegenOperation extends CodegenOperation {
        private List<String> pathTemplateNames = new ArrayList<String>();
        private String replacedPathName;

        public ExtendedCodegenOperation(CodegenOperation o) {
            super();

            // Copy all fields of CodegenOperation
            this.responseHeaders.addAll(o.responseHeaders);
            this.hasAuthMethods = o.hasAuthMethods;
            this.hasConsumes = o.hasConsumes;
            this.hasProduces = o.hasProduces;
            this.hasParams = o.hasParams;
            this.hasOptionalParams = o.hasOptionalParams;
            this.returnTypeIsPrimitive = o.returnTypeIsPrimitive;
            this.returnSimpleType = o.returnSimpleType;
            this.subresourceOperation = o.subresourceOperation;
            this.isMapContainer = o.isMapContainer;
            this.isListContainer = o.isListContainer;
            this.isMultipart = o.isMultipart;
            this.hasMore = o.hasMore;
            this.isResponseBinary = o.isResponseBinary;
            this.hasReference = o.hasReference;
            this.isRestfulIndex = o.isRestfulIndex;
            this.isRestfulShow = o.isRestfulShow;
            this.isRestfulCreate = o.isRestfulCreate;
            this.isRestfulUpdate = o.isRestfulUpdate;
            this.isRestfulDestroy = o.isRestfulDestroy;
            this.isRestful = o.isRestful;
            this.path = o.path;
            this.operationId = o.operationId;
            this.returnType = o.returnType;
            this.httpMethod = o.httpMethod;
            this.returnBaseType = o.returnBaseType;
            this.returnContainer = o.returnContainer;
            this.summary = o.summary;
            this.unescapedNotes = o.unescapedNotes;
            this.notes = o.notes;
            this.baseName = o.baseName;
            this.defaultResponse = o.defaultResponse;
            this.discriminator = o.discriminator;
            this.consumes = o.consumes;
            this.produces = o.produces;
            this.bodyParam = o.bodyParam;
            this.allParams = o.allParams;
            this.bodyParams = o.bodyParams;
            this.pathParams = o.pathParams;
            this.queryParams = o.queryParams;
            this.headerParams = o.headerParams;
            this.formParams = o.formParams;
            this.authMethods = o.authMethods;
            this.tags = o.tags;
            this.responses = o.responses;
            this.imports = o.imports;
            this.examples = o.examples;
            this.externalDocs = o.externalDocs;
            this.vendorExtensions = o.vendorExtensions;
            this.nickname = o.nickname;
            this.operationIdLowerCase = o.operationIdLowerCase;
            this.operationIdCamelCase = o.operationIdCamelCase;
        }

        public List<String> getPathTemplateNames() {
            return pathTemplateNames;
        }

        public void setPathTemplateNames(List<String> pathTemplateNames) {
            this.pathTemplateNames = pathTemplateNames;
        }

        public String getReplacedPathName() {
            return replacedPathName;
        }

        public void setReplacedPathName(String replacedPathName) {
            this.replacedPathName = replacedPathName;
        }

        public String typespec() {
            StringBuilder sb = new StringBuilder("@spec ");
            sb.append(underscore(operationId));
            sb.append("(Tesla.Env.client, ");

            for (CodegenParameter param : allParams) {
                if (param.required) {
                    buildTypespec(param, sb);
                    sb.append(", ");
                }
            }

            sb.append("keyword()) :: {:ok, ");
            if (returnBaseType == null) {
                sb.append("nil");
            } else if (returnSimpleType) {
                if (!returnTypeIsPrimitive) {
                    sb.append(moduleName);
                    sb.append(".Model.");
                }
                sb.append(returnBaseType);
                sb.append(".t");
            } else if (returnContainer == null) {
                sb.append(returnBaseType);
                sb.append(".t");
            } else {
                if (returnContainer.equals("array")) {
                    sb.append("list(");
                    if (!returnTypeIsPrimitive) {
                        sb.append(moduleName);
                        sb.append(".Model.");
                    }
                    sb.append(returnBaseType);
                    sb.append(".t)");
                } else if (returnContainer.equals("map")) {
                    sb.append("map()");
                }
            }
            sb.append("} | {:error, Tesla.Env.t}");
            return sb.toString();
        }

        private void buildTypespec(CodegenParameter param, StringBuilder sb) {
            if (param.dataType == null) {
                sb.append("nil");
            } else if (param.isListContainer) {
                // list(<subtype>)
                sb.append("list(");
                if (param.isBodyParam) {
                    buildTypespec(param.items.items, sb);
                } else {
                    buildTypespec(param.items, sb);
                }
                sb.append(")");
            } else if (param.isMapContainer) {
                // %{optional(String.t) => <subtype>}
                sb.append("%{optional(String.t) => ");
                buildTypespec(param.items, sb);
                sb.append("}");
            } else if (param.isPrimitiveType) {
                // like `integer()`, `String.t`
                sb.append(param.dataType);
            } else if (param.isFile) {
                sb.append("String.t");
            } else {
                // <module>.Model.<type>.t
                sb.append(moduleName);
                sb.append(".Model.");
                sb.append(param.dataType);
                sb.append(".t");
            }
        }
        private void buildTypespec(CodegenProperty property, StringBuilder sb) {

            if (getBooleanValue(property, CodegenConstants.IS_LIST_CONTAINER_EXT_NAME)) {
                sb.append("list(");
                buildTypespec(property.items, sb);
                sb.append(")");
            } else if (getBooleanValue(property, CodegenConstants.IS_MAP_CONTAINER_EXT_NAME)) {
                sb.append("%{optional(String.t) => ");
                buildTypespec(property.items, sb);
                sb.append("}");
            } else if (getBooleanValue(property, CodegenConstants.IS_PRIMITIVE_TYPE_EXT_NAME)) {
                sb.append(property.baseType);
                sb.append(".t");
            } else {
                sb.append(moduleName);
                sb.append(".Model.");
                sb.append(property.baseType);
                sb.append(".t");
            }
        }

        public String decodedStruct() {
            // Let Poison decode the entire response into a generic blob
            if (isMapContainer) {
                return "";
            }
            // Primitive return type, don't even try to decode
            if (returnBaseType == null || (returnSimpleType && returnTypeIsPrimitive)) {
                return "false";
            }
            StringBuilder sb = new StringBuilder();
            if (isListContainer) {
                sb.append("[");
            }
            sb.append("%");
            sb.append(moduleName);
            sb.append(".Model.");
            sb.append(returnBaseType);
            sb.append("{}");
            if (isListContainer) {
                sb.append("]");
            }
            return sb.toString();
        }
    }

    class ExtendedCodegenModel extends CodegenModel {
        public boolean hasImports;
        public ExtendedCodegenModel(CodegenModel cm) {
            super();

            // Copy all fields of CodegenModel
            this.parent = cm.parent;
            this.parentSchema = cm.parentSchema;
            this.parentModel = cm.parentModel;
            this.interfaceModels = cm.interfaceModels;
            this.children = cm.children;
            this.name = cm.name;
            this.classname = cm.classname;
            this.title = cm.title;
            this.description = cm.description;
            this.classVarName = cm.classVarName;
            this.modelJson = cm.modelJson;
            this.dataType = cm.dataType;
            this.xmlPrefix = cm.xmlPrefix;
            this.xmlNamespace = cm.xmlNamespace;
            this.xmlName = cm.xmlName;
            this.classFilename = cm.classFilename;
            this.unescapedDescription = cm.unescapedDescription;
            this.discriminator = cm.discriminator;
            this.defaultValue = cm.defaultValue;
            this.arrayModelType = cm.arrayModelType;
            // TODO: use vendor extension this.isAlias = cm.isAlias;
            this.vars = cm.vars;
            this.requiredVars = cm.requiredVars;
            this.optionalVars = cm.optionalVars;
            this.readOnlyVars = cm.readOnlyVars;
            this.readWriteVars = cm.readWriteVars;
            this.allVars = cm.allVars;
            this.parentVars = cm.parentVars;
            this.allowableValues = cm.allowableValues;
            this.mandatory = cm.mandatory;
            this.allMandatory = cm.allMandatory;
            this.imports = cm.imports;
            this.emptyVars = cm.emptyVars;
            this.externalDocumentation = cm.externalDocumentation;
            this.vendorExtensions = cm.vendorExtensions;
            this.additionalPropertiesType = cm.additionalPropertiesType;

            this.hasImports = !this.imports.isEmpty();
        }

        public boolean hasComplexVars() {
            for (CodegenProperty p : vars) {
                if (!getBooleanValue(p, CodegenConstants.IS_PRIMITIVE_TYPE_EXT_NAME)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        // no need to escape as Elixir does not support multi-line comments
        return input;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
}
