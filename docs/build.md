# Compilación

A continuación, se indican los pasos que hay que seguir para llevar a cabo la generación del artefacto.

## Prerrequisitos

Se precisa disponer los siguientes elementos configurados:

* OpenJDK 11
* Maven 3.6.x

## Compilación

El siguiente se ejecuta comando para realizar la compilación:

```bash
mvn clean package
```

También es posible instalar o desplegar los artefactos sustituyendo `package` por `install` o `deploy` respectivamente.

Los artefactos se generarán dentro del directorio `target` de cada uno de los módulos:

### Back

Los artefactos se encuentran dentro de triples-storage-adapter-back/target

* Artefacto: triples-storage-adapter-back-{version}.jar

### Service

Los artefactos se encuentran dentro de triples-storage-adapter-service/target

* Artefacto: triples-storage-adapter-service-{version}.jar

### Service Abstractions

Los artefactos se encuentran dentro de triples-storage-adapter-service-abstractions/target

* Artefacto: triples-storage-adapter-service-abstractions-{version}.jar

### Swagger

Los artefactos se encuentran dentro de triples-storage-adapter-swagger/target

* Artefacto: triples-storage-adapter-swagger-{version}.jar

### Audit

Los artefactos se encuentran dentro de triples-storage-adapter-audit/target

* Artefacto: triples-storage-adapter-audit-{version}.jar

