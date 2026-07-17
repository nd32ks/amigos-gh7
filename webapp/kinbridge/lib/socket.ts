import { Server as IOServer } from "socket.io";
import type { Server as HTTPServer } from "http";
import type { NextApiResponse } from "next";

let io: IOServer | null = null;

export function initSocket(server: HTTPServer): IOServer {
  if (io) return io;

  io = new IOServer(server, {
    path: "/ws/alerts",
    cors: {
      origin: "*",
      methods: ["GET", "POST"],
    },
  });

  io.on("connection", (socket) => {
    console.log("Socket connected:", socket.id);
    socket.on("disconnect", () => {
      console.log("Socket disconnected:", socket.id);
    });
  });

  return io;
}

export function getIo(): IOServer | null {
  return io;
}

export function broadcast(event: string, payload: unknown) {
  if (io) {
    io.emit(event, payload);
  }
}

export function withSocket(res: NextApiResponse, handler: (io: IOServer) => void) {
  const socketIo = getIo();
  if (socketIo) {
    handler(socketIo);
  }
}
