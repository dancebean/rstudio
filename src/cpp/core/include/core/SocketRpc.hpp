/*
 * SocketRpc.hpp
 *
 * Copyright (C) 2017 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#ifndef SOCKET_RPC_HPP
#define SOCKET_RPC_HPP

#include <core/json/Json.hpp>

#define kServerRpcSocketPath          "/tmp/rstudio-rserver/session-server-rpc.socket"
#define kServerRpcSecretHeader        "X-RS-Session-Server-RPC-Secret"
#define kServerRpcSecretEnvVar        "RS_SESSION_SERVER_RPC_SECRET"
#define kRstudioRpcCookieHeader       "X-RS-Session-Server-RPC-Cookie"
#define kRstudioRpcCookieEnvVar       "RS_SESSION_RPC_COOKIE"
#define kRstudioMessageSignature      "X-RS-Message-Signature"

namespace rstudio {
namespace core {

class Error;
class FilePath;

namespace socket_rpc {

core::Error initialize();
core::Error initializeSecret(const std::string& rpcSecret);

core::Error invokeRpc(const FilePath& socketPath,
                      const std::string& endpoint,
                      const core::json::Object& request,
                      core::json::Value *pResult);

Error invokeRpc(const std::string& address,
                const std::string& port,
                bool ssl,
                const std::string& endpoint,
                const json::Object& request,
                json::Value *pResult);

const std::string& secret();

} // namespace socket_rpc
} // namespace core
} // namespace rstudio

#endif // SOCKET_RPC_HPP


