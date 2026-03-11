# Chat requirements

## Business requirements
The following requirements describe user-visible behavior and guarantees the system must provide.

### Sending message
- User can send message to participant(s) in the chat.
- Sent message appears in chat on local device immediately.
- All messages sent by a user must eventually be delivered to the recipient(s) unless explicitly deleted or blocked.
- User must be informed of failed message sending.
- User must be able to retry sending failed messages.
- Messages that fail to be delivered to the backend must remain visible in the chat with a failed status and allow the user to retry sending them.
- When a message is read by any participant of the chat, the sender must see that message as read.

### Message content and validation
- Message must not be blank.
- Message text must be less or equal to 4000 characters.

### Message lifecycle and visibility
- Users must see messages that have already been loaded when the chat screen is displayed.
- User can scroll to see older messages without manual refresh.
- User must see who sent a message and when.
- Users must be able to see which participants have read a message.
- Incoming messages must appear in the chat automatically without user intervention.
- When a user reads a message, that message and all earlier messages must be marked as read for other participants.
- Messages must appear in a consistent chronological order for all participants.
- Users must be able to read recent messages offline.
- Only participants of a conversation must be able to access its messages.
- Users must see the same messages on all devices associated with their account.
- Messages deleted by the backend must be removed or marked unavailable.
- Users must not see duplicate messages in a conversation.
- Messages missed due to temporary connectivity issues must appear in the conversation once the connection is restored.
- Users must be able to edit previously sent messages and see that a message has been edited.

### Presence indicators
- User must see online/offline status of participants.
- User must see that other participants are typing.

## UX rules
The following rules define expected user experience and interface behavior.

- The chat screen should display recently loaded messages quickly after opening.
- While a message is being sent, the message input should not allow edits that could change the content of the message being sent.
- After a message is successfully added to the chat, the input field should be cleared.
- If message not added to chat then error must be shown to the user, user input must be kept and enabled for modification. 
- Only one blocking error should be shown to the user at a time.
- Blocking errors should require user acknowledgement before dismissal.
- A message is considered read when the user has visibly viewed at least
100 consecutive characters or 50% of viewport of the message for at least 1 second without interruption.
- User must be informed about his/her invalid messages.

## System requirements
- Messages created while the device is offline must be delivered once connectivity is restored.
- Messages must have stable identities so the system can reconcile retries,
synchronization between devices, and message updates.
- Message delivery must be idempotent: retries on sending the same message must not create duplicating messages.
- Input must be validated on server before it appears for other users.

## Non-functional requirements
The following requirements describe performance and scalability expectations.

- On the supported baseline device and network profile, the latest messages must become visible within 500 ms at p95 after opening the chat.  
- With conversations containing at least 10,000 messages, scrolling and input must remain responsive within the agreed UI performance budget (for example, no dropped input events and frame time within the team's target threshold).
