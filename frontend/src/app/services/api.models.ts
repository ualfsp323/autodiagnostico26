export type ChatRoomType = 'SEGUIMIENTO';
export type ChatSenderRole = 'MECANICO' | 'USUARIO';

export interface ChatJoinResponse {
  roomType: ChatRoomType;
  participantId: number;
  activeUsers: number;
  maxUsers: number;
  joined: boolean;
}

export interface ChatMessageRequest {
  participantId: number;
  roomType: ChatRoomType;
  senderRole: ChatSenderRole;
  commentText: string;
}

export interface ChatMessageResponse {
  id: number;
  roomType: ChatRoomType;
  participantId: number;
  senderRole: ChatSenderRole;
  commentText: string;
  wordCount: number;
  readByUser: boolean;
  createdAt: string;
}
