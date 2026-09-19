package co.com.bancolombia.model.user.gateways;

import co.com.bancolombia.model.user.DeleteUserEntry;

import java.util.List;

public interface DeleteUserParser {

    List<DeleteUserEntry> parse(String content);
}
