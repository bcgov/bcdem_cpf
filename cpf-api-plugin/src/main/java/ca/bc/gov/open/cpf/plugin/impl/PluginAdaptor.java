package ca.bc.gov.open.cpf.plugin.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.log4j.Logger;

import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.api.security.SecurityService;

import com.revolsys.gis.cs.GeometryFactory;
import com.revolsys.gis.data.model.Attribute;
import com.revolsys.gis.data.model.AttributeProperties;
import com.revolsys.gis.data.model.DataObjectMetaData;
import com.revolsys.gis.data.model.DataObjectMetaDataImpl;
import com.revolsys.io.LazyHttpPostOutputStream;
import com.revolsys.util.ExceptionUtil;
import com.revolsys.util.JavaBeanUtil;
import com.vividsolutions.jts.geom.Geometry;

public class PluginAdaptor {

  private static final String[] INPUT_DATA_PARAMETER_NAMES = new String[] {
    "inputDataUrl", "inputDataContentType"
  };

  private final Object plugin;

  private final BusinessApplication application;

  private Map<String, Object> responseFields;

  private SecurityService securityService;

  private final List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

  private static final List<String> INTERNAL_PROPERTY_NAMES = Arrays.asList(
    "sequenceNumber", "resultNumber");

  private final Map<String, Object> parameters = new HashMap<String, Object>();

  private final AppLog appLog;

  public PluginAdaptor(final BusinessApplication application,
    final Object plugin, final String logLevel) {
    this.application = application;
    this.plugin = plugin;
    appLog = new AppLog(logLevel);
    try {
      final Class<? extends Object> pluginClass = plugin.getClass();
      final Method setAppLogMethod = pluginClass.getMethod("setAppLog",
        AppLog.class);
      try {
        setAppLogMethod.invoke(plugin, appLog);
      } catch (final IllegalAccessException e) {
        ExceptionUtil.throwCauseException(e);
      } catch (final InvocationTargetException e) {
        ExceptionUtil.throwCauseException(e);
      }
    } catch (final NoSuchMethodException e) {
    }
  }

  public void execute() {
    try {
      MethodUtils.invokeExactMethod(plugin, "execute", new Object[0]);
    } catch (final InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      } else if (cause instanceof Error) {
        throw (Error)cause;
      } else {
        throw new RuntimeException("Unable to invoke execute on "
          + application.getName(), cause);
      }
    } catch (final Throwable t) {
      throw new RuntimeException("Unable to invoke execute on "
        + application.getName(), t);
    }
    final String resultListProperty = application.getResultListProperty();
    if (resultListProperty == null) {
      this.responseFields = getResult(plugin);
      results.add(responseFields);
    } else {
      final List<Object> resultObjects = JavaBeanUtil.getProperty(plugin,
        resultListProperty);
      if (resultObjects != null) {
        for (final Object resultObject : resultObjects) {
          final Map<String, Object> result = getResult(resultObject);
          results.add(result);
        }
      }
    }
  }

  public BusinessApplication getApplication() {
    return application;
  }

  public AppLog getAppLog() {
    return appLog;
  }

  public Map<String, Object> getResponseFields() {
    return responseFields;
  }

  private Map<String, Object> getResult(final Object resultObject) {
    final DataObjectMetaData resultMetaData = application.getResultMetaData();
    final Map<String, Object> result = new HashMap<String, Object>();
    for (final Attribute attribute : resultMetaData.getAttributes()) {
      final String fieldName = attribute.getName();
      if (!INTERNAL_PROPERTY_NAMES.contains(fieldName)) {
        Object value;
        try {
          value = PropertyUtils.getSimpleProperty(resultObject, fieldName);
        } catch (final Throwable t) {
          throw new IllegalArgumentException("Could not read property "
            + application.getName() + "." + fieldName, t);
        }
        if (value != null) {
          if (value instanceof Geometry) {
            Geometry geometry = (Geometry)value;
            GeometryFactory geometryFactory = attribute.getProperty(AttributeProperties.GEOMETRY_FACTORY);
            if (geometryFactory == GeometryFactory.getFactory()) {
              geometryFactory = GeometryFactory.getFactory(geometry);
            }
            int srid = geometryFactory.getSRID();
            if (parameters.containsKey("resultSrid")) {
              int resultSrid = ((Number)parameters.get("resultSrid")).intValue();
              if (resultSrid != 0) {
                srid = resultSrid;
              }
            }
            int numAxis = geometryFactory.getNumAxis();
            if (parameters.containsKey("resultNumAxis")) {
              int resultNumAxis = ((Number)parameters.get("resultNumAxis")).intValue();
              if (resultNumAxis != 0) {
                numAxis = resultNumAxis;
              }
            }
            double scaleXY = geometryFactory.getScaleXY();
            if (parameters.containsKey("resultScaleFactorXy")) {
              double resultScaleFactorXy = ((Number)parameters.get("resultScaleFactorXy")).doubleValue();
              if (resultScaleFactorXy != 0) {
                scaleXY = resultScaleFactorXy;
              }
            }
            double scaleZ = geometryFactory.getScaleZ();
            if (parameters.containsKey("resultScaleFactorZ")) {
              double resultScaleFactorZ = ((Number)parameters.get("resultScaleFactorZ")).doubleValue();
              if (resultScaleFactorZ != 0) {
                scaleZ = resultScaleFactorZ;
              }
            }
            geometryFactory = GeometryFactory.getFactory(srid, numAxis,
              scaleXY, scaleZ);
            geometry = geometryFactory.createGeometry(geometry);
            if (geometry.getSRID() == 0) {
              throw new IllegalArgumentException(
                "Geometry does not have a coordinate system (SRID) specified");
            }
            final Boolean validateGeometry = attribute.getProperty(AttributeProperties.VALIDATE_GEOMETRY);
            if (validateGeometry == true) {
              if (!geometry.isValid()) {
                throw new IllegalArgumentException("Geometry is not valid for"
                  + application.getName() + "." + fieldName);
              }
            }
            result.put(fieldName, geometry);
          } else {
            result.put(fieldName, value);
          }
        }

      }
    }
    if (application.isHasCustomizationProperties()) {
      Object value = JavaBeanUtil.getValue(resultObject,
        "customizationProperties");
      if (value != null) {
        result.put("customizationProperties", value);
      }
    }
    return result;
  }

  public List<Map<String, Object>> getResults() {
    return results;
  }

  public SecurityService getSecurityService() {
    return securityService;
  }

  public void setParameters(final Map<String, ? extends Object> parameters) {
    final DataObjectMetaDataImpl requestMetaData = application.getRequestMetaData();
    for (final Attribute attribute : requestMetaData.getAttributes()) {
      final String parameterName = attribute.getName();
      final Object parameterValue = parameters.get(parameterName);
      if (parameterValue == null) {
        if (attribute.isRequired()) {
          throw new IllegalArgumentException(parameterName + " is required");
        }
      } else {
        setPluginProperty(parameterName, parameterValue);
      }
    }
    if (application.isPerRequestInputData()) {
      for (final String parameterName : INPUT_DATA_PARAMETER_NAMES) {
        final Object parameterValue = parameters.get(parameterName);
        setPluginProperty(parameterName, parameterValue);
      }
    }
    if (application.isPerRequestResultData()) {
      final String resultDataContentType = (String)parameters.get("resultDataContentType");
      final String resultDataUrl = (String)parameters.get("resultDataUrl");
      OutputStream resultData;
      if (resultDataUrl == null) {
        resultData = (OutputStream)parameters.get("resultData");
        if (resultData == null) {
          try {
            final File file = File.createTempFile("cpf", ".out");
            resultData = new FileOutputStream(file);
            Logger.getLogger(getClass()).info("Writing result to " + file);
          } catch (final IOException e) {
            resultData = System.out;
          }
        }
      } else {
        resultData = new LazyHttpPostOutputStream(resultDataUrl,
          resultDataContentType);
      }
      setPluginProperty("resultData", resultData);
      setPluginProperty("resultDataContentType", resultDataContentType);
    }
    if (application.isSecurityServiceRequired()) {
      if (securityService == null) {
        throw new IllegalArgumentException("Security service is required");
      } else {
        setPluginProperty("securityService", securityService);
      }
    }
  }

  public void setPluginProperty(final String parameterName,
    final Map<String, ? extends Object> parameters) {
    final Object parameterValue = parameters.get(parameterName);
    setPluginProperty(parameterName, parameterValue);
  }

  public void setPluginProperty(final String parameterName,
    final Object parameterValue) {
    try {
      BeanUtils.setProperty(this.plugin, parameterName, parameterValue);
      this.parameters.put(parameterName, parameterValue);
    } catch (final Throwable t) {
      throw new IllegalArgumentException(this.application.getName() + "."
        + parameterName + " could not be set", t);
    }
  }

  public void setSecurityService(final SecurityService securityService) {
    this.securityService = securityService;
  }

  @Override
  public String toString() {
    return application.getName() + ": " + plugin.toString();
  }
}