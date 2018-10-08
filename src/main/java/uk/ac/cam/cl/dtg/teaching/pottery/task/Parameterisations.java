/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.cam.cl.dtg.teaching.pottery.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.stringtemplate.v4.Interpreter;
import org.stringtemplate.v4.ModelAdaptor;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.misc.STNoSuchPropertyException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.InvalidParameterisationException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.MutationParameter;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Parameterisation;

public class Parameterisations {
  public static int getCount(Parameterisation parameterisation)
      throws InvalidParameterisationException {
    int count = 1;
    Map<String, Integer> valueCounts = parameterisation.getValues().entrySet().stream()
        .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().size()));
    for (MutationParameter parameter : parameterisation.getParameters()) {
      if (!valueCounts.containsKey(parameter.getValue())) {
        throw new InvalidParameterisationException(parameter.getValue() + " is not a known value");
      }
      int valueCount = valueCounts.get(parameter.getValue());
      if (valueCount == 0) {
        throw new InvalidParameterisationException(parameter.getValue()
            + " does not have enough values provided");
      }
      count *= valueCount;
      valueCount--;
      valueCounts.put(parameter.getValue(), valueCount);
    }
    return count;
  }

  public static ObjectNode generateParameters(Parameterisation parameterisation, int mutation)
      throws InvalidParameterisationException {
    Map<String, List<Integer>> valueContents = parameterisation.getValues().entrySet().stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey(),
            entry ->
                IntStream.range(0, entry.getValue().size()).boxed().collect(Collectors.toList())
        ));

    ObjectNode parameters = JsonNodeFactory.instance.objectNode();
    int runningOffset = 0;
    for (MutationParameter parameter : parameterisation.getParameters()) {
      String valueName = parameter.getValue();
      List<Integer> valueIndexes = valueContents.get(valueName);
      if (valueIndexes == null) {
        throw new InvalidParameterisationException(parameter.getValue() + " is not a known value");
      }
      int valueCount = valueIndexes.size();
      if (valueCount == 0) {
        throw new InvalidParameterisationException(parameter.getValue()
            + " does not have enough values provided");
      }
      int valueIndex = (mutation + runningOffset) % valueCount;
      runningOffset += mutation % valueCount + 1;
      int index = valueIndexes.remove(valueIndex);
      mutation /= valueCount;
      parameters.put(parameter.getName(), parameterisation.getValues().get(valueName).get(index));
    }
    return parameters;
  }

  public static String makeSubstitutions(String template, JsonNode values) {
    STGroup stg = new STGroup('¡', '¡');
    stg.registerModelAdaptor(JsonNode.class, (interpreter, st, o, objKey, key) -> {
      JsonNode node = (JsonNode) o;
      JsonNode subNode = node.get(key);
      if (subNode == null && objKey instanceof Integer) {
        subNode = node.get((int) objKey);
      }
      if (subNode != null) {
        return collapseNodes(subNode);
      }
      return null;
    });

    ST st = new ST(stg, template);

    values.fields().forEachRemaining(value ->
      st.add(value.getKey(), collapseNodes(value.getValue()))
    );

    return st.render();
  }

  private static Object collapseNodes(JsonNode value) {
    if (value.isTextual()) {
      return value.textValue();
    }
    if (value.isArray()) {
      return StreamSupport.stream(value.spliterator(), false)
          .map(node -> collapseNodes(node))
          .collect(Collectors.toList());
    }
    return value;
  }
}
