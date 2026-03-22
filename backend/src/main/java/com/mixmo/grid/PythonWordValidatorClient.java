package com.mixmo.grid;

import java.util.List;

public interface PythonWordValidatorClient {
  List<GridValidationModels.PythonValidationResult> validateBatch(List<String> words);
}
