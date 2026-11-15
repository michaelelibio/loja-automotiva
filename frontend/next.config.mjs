/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Supplier media is rendered unchanged; do not expose an unused server image optimizer.
  images: { unoptimized: true },

  allowedDevOrigins: [
    "scheduled-thesis-responsibilities-wondering.trycloudflare.com",
    "192.168.1.4",
  ],
};

export default nextConfig;
