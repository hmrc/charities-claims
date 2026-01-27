# Schematron Validation Approach for F23

## Context

ChRIS submissions require Schematron validation (business rules) after XSD validation (schema). Currently this validation lives in the external `charities-validator` service. Per Solution Architect guidance, all validation should be consolidated into charities-claims backend.

The spike evaluated three approaches for implementing the Schematron rules.

## Approaches Evaluated

### Approach A: Domain Model Validation

Validate `GovTalkMessage` case class directly (before XML serialization).

**Implementation**: `app/uk/gov/hmrc/charitiesclaims/validation/model/ModelSchematronValidator.scala`

**POC Results**:
- 5 representative rules implemented (ClaimRule, AuthOfficialRule, DateRule, KeyRule, AggDonationRule)
- Clean, type-safe solution

| Aspect | Assessment |
|--------|------------|
| Type safety | Strong - compiler catches field access errors |
| Testability | Easy - just pass model instances |
| Effort | Medium |

### Approach B: XML String Validation (XPath/DOM)

Validate after XML serialization using XPath/DOM queries.

**Implementation**: `app/uk/gov/hmrc/charitiesclaims/validation/xml/XmlSchematronValidator.scala`

**POC Results**:
- Same 5 rules implemented

| Aspect | Assessment |
|--------|------------|
| Type safety | Weak - XPath strings can fail at runtime |
| Testability | Requires XML fixtures |
| Notes | Matches XSD validation pattern |
| Effort | Very Hard |

### Approach C: GSL Schematron JAR (Legacy Reuse)

Use the existing GSL Schematron engine from [charities-validator](https://github.com/hmrc/charities-validator).

**Initial Investigation**:
- `charities-validator` is a Java/Spring application (not a library)
- Uses proprietary `com.gsl:GSLSchematronValidator-nojoda:2.0.0` from HMRC internal Maven repo
- Schematron rules are precompiled in `HMRC-Charities:2.0.0` artifact (binary format, not source)
- All rules available as precompiled Java classes in the JAR

**Deep Dive Investigation** (attempted direct JAR integration):

**What worked**:
- Both JARs accessible from `artefacts.tax.service.gov.uk`
- Dependencies resolve in sbt with internal Maven resolver
- Code compiles successfully
- API discovered via decompilation:
  ```scala
  val factory = new SchematronValidatorFactoryImpl(params, RuleGeneratorMode.LOAD_FROM_CLASSPATH)
  val validator = factory.createValidator()
  val result = validator.validateSubmission(inputStream, outputStream)
  ```

**What failed**:
- Runtime initialization fails with `NullPointerException` in `JDTByteCodeGenerator`
- Even with Eclipse JDT dependency added, factory creation fails
- The GSL validator expects specific runtime configuration that isn't documented

**Root cause**: The GSL validator has deep integration requirements:
- Requires Eclipse JDT for dynamic class compilation (even with precompiled rules)
- Expects specific configuration file paths and temp directories
- Has undocumented runtime dependencies
- The `charities-validator` has full Spring context wiring

**POC Code** (blocked, tests ignored):
- `app/uk/gov/hmrc/charitiesclaims/validation/gsl/GslSchematronValidator.scala`
- `test/uk/gov/hmrc/charitiesclaims/validation/gsl/GslSchematronValidatorSpec.scala` (@Ignore)

| Aspect | Assessment |
|--------|------------|
| Type safety | N/A - black box validation |
| Testability | Itegration test with XML fixtures |
| Dependencies | Proprietary JARs, undocumented runtime config |
| Effort | **Unknown** - blocked by runtime issues |

## Decision

We will use **Approach A: Domain Model Validation** - validating the `GovTalkMessage` case class directly before XML serialization.

## Rationale

### Approach A (Domain Model)?

1. **Type Safety**: The compiler catches field access errors at build time

2. **Maintainability**: Clean Scala code with pattern matching on case classes is easier to read, modify, and review than XPath strings or proprietary JARs.

3. **Testability**: Unit tests are simpler - just construct `GovTalkMessage` instances and verify validation results. No XML fixtures required.

4. **Performance**: No additional XML parsing required. Approach B would need to parse the XML again (XSD validation already parsed it once).

5. **Independence**: No external dependencies, services, or proprietary libraries required.

### Approach B (XML)?

Approach B has downsides:
- XPath strings are not type safe (only fail at runtime)
- Requires a second XML parse (XSD validation already parsed once)
- Namespace handling adds complexity
- Harder to test without XML fixtures

Approach B remains viable if there's a preference for keeping all validation against XML, but the type safety and testability benefits of Approach A outweigh the consistency argument.

### Approach C (JAR)?

The integration challenges outweigh the benefit of rule reuse:
- Proprietary `GSLSchematronValidator` JAR from GSL (Government Services Limited)
- Rules in binary/precompiled format, not inspectable
- Integration with Scala 3 / sbt ecosystem is uncertain

## Implementation Plan

### Integration Point

Add model validation in `ChRISConnector` after XSD validation. Logically, Schematron (business rules) should run after XSD (structure) passes:

```scala
// ChRISConnector
final def submitClaim(govTalkMessage: GovTalkMessage): Future[Unit] =
  val xml = XmlWriter.writeCompact(govTalkMessage)
  Future
    .fromTry(XmlUtils.validateChRISSubmission(xml))          // 1. XSD validation
    .flatMap(_ =>
      ModelSchematronValidator.validate(govTalkMessage) match // 2. Schematron validation
        case Left(errors) => Future.failed(SchematronException(errors))
        case Right(_)     => http.post(...)                   // 3. Submit to ChRIS
    )
```

## POC Code Location

- Shared: `app/uk/gov/hmrc/charitiesclaims/validation/`
- Approach A: `app/uk/gov/hmrc/charitiesclaims/validation/model/`
- Approach B: `app/uk/gov/hmrc/charitiesclaims/validation/xml/`
- Approach C: `app/uk/gov/hmrc/charitiesclaims/validation/gsl/` (blocked, tests @Ignore)
- Tests: `test/uk/gov/hmrc/charitiesclaims/validation/`
