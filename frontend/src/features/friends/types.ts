export type FriendPresence = {
  id: string;
  username: string;
  avatarUrl?: string | null;
  level?: number;
  online: boolean;
  currentGameSlug?: string | null;
  currentGameName?: string | null;
};

export type FriendRequest = {
  id: string;
  sender: FriendPresence;
  recipient: FriendPresence;
  status: "PENDING";
  createdAt: string;
};

export type FriendRequestsResponse = {
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
};
