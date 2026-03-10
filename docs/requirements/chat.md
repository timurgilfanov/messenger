# Chat requirements

## Business requirements
The following requirements describe user-visible behavior and guarantees the system must provide.

### Sending message
- User able to send message if chat is shown and has no cool down period for the user.
- Sent message appears in chat on local device immediately.
- If message not added to chat then error must be shown to the user, input must be filled and enabled for modification.
- Message appeared in the chat on local device must be validated on the backend and delivered to all participants.
- User must be informed of failed message sending to the backend.
- User must be able to retry sending failed messages.
- When a message is read by any participant of the chat, the sender must see that message as read.
- Messages created while the device is offline must be delivered once connectivity is restored.
- Messages that fail to be delivered to the backend must remain visible in the chat with a failed status and allow the user to retry sending them.

### Input validation
- Input must be validated on server before it appears for other users.
- User must be informed about his/her invalid messages.

### Message validation
- Message must be not blank.
- Message text must be less or equal to 4000 characters.

### Messages
- Users must see messages that have already been loaded when the chat screen is displayed.
- User can scroll to see older messages without manual refresh.
- User must see who sent a message and when.
- User must be able to see participants that read any message in the chat.
- Incoming messages must appear in the chat without manual refresh.
- When a user sees the message for 1 second, this message and all messages before that must be marked as read by this user for other participants of the chat.
- If a user is forbidden to see the chat, the user must see an error instead of messages.
- Messages must appear in a consistent chronological order for all participants.
- Users must be able to read recent messages offline.
- Message data must be protected from unauthorized access.
- Messages must be synchronized across all devices of a user.
- Messages deleted by the backend must be removed or marked unavailable.
- Users must be able to edit previously sent messages and see that a message has been edited.
- Messages must remain uniquely identifiable across retries and synchronization between devices.
- Message delivery must be idempotent: retries on sending the same message must not create duplicating messages.
- When messages are missed due to temporary connectivity issues, the system must recover and display the missing messages when synchronization is restored.
- Message ordering must remain consistent even when participants send messages from different devices or when network delays occur.

### Presence indicators
- User must see online/offline status of participants.
- User must see that other participants are typing.

## UX rules
The following rules define expected user experience and interface behavior.

- When sending messages with binary payloads, users must see progress of the payload upload or saving operation.
- The chat screen should display recently loaded messages quickly after opening.
- While a message is being sent, the message input should not allow edits that could change the content of the message being sent.
- After a message is successfully added to the chat, the input field should be cleared.
- Only one blocking error should be shown to the user at a time.
- Blocking errors should require user acknowledgement before dismissal.

## Non-functional requirements
The following requirements describe performance and scalability expectations.

- The latest messages should be visible within 500 ms after opening the chat when network and device conditions are normal.
- The chat must support at least 10,000 messages per conversation without noticeable UI degradation.
