import "./globals.css";
import { Navbar } from "../components/Navbar";

export const metadata = {
  title: "SentinelCam - 24x7 Ultra-Low Latency CCTV Dashboard",
  description: "WebRTC 24x7 Android CCTV Node Web Management System",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <div className="app-container">
          <Navbar />
          <main className="main-content">{children}</main>
        </div>
      </body>
    </html>
  );
}
