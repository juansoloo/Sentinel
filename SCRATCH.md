# Scratch Notes

## Current State

- App compiles.
- Proxy flow now routes row selection and context menu actions through `ProxyController`.
- `ProxyView` no longer owns the request history list.
- `ProxyModel` is the source of truth for captured transactions.
- `Send to Repeater` loads the selected request into the Repeater tab.
- Tested manually: headers/request text appeared correctly in Repeater.

## Next Steps

1. Automatically switch to the Repeater tab after `Send to Repeater`.
2. Add a way for `RepeaterView` to expose the edited request text.
3. Extract or reuse request parsing logic so edited repeater text can become a `ProxyRequest`.
4. Build a repeater sender service that sends a `ProxyRequest` using a new socket connection.
5. Render the repeater response using `HttpMessageFormatter.renderResponse(...)`.
6. Wire the Repeater `SEND` button through the controller.
7. Remove temporary/commented code and unused methods from controller/views.
8. Consider making `HttpTransaction` snapshots deep-copy request/response data if history editing becomes possible.

