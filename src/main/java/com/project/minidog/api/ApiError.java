package com.project.minidog.api;

import java.util.List;

public record ApiError(String error, List<String> details) {
}
