package co.com.bancolombia.model.user.gateways;

import co.com.bancolombia.model.user.ChangeNameEntry;

import java.util.List;

public interface ChangeNameParser {

    List<ChangeNameEntry> parse(String content);
}
