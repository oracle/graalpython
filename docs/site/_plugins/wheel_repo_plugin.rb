# frozen_string_literal: true

# Plugin that generates a Python repository index for GraalPy wheels at
# /python/wheels. The input is the graalpy_wheels.txt file next to this file.
# The wheels themselves are hosted in GDS.
module WheelRepoPlugin
  TEMPLATE = <<~EOF
  <!doctype html>
  <html>
    <head>
      <title>GraalPy wheel repository</title>
    </head>
    <body>
      <pre>
      %{content}
      </pre>
    </body>
  </html>
  EOF

  Wheel = Struct.new(:name, :filename)

  class IndexGenerator < Jekyll::Generator
    safe true

    def generate(site)
      index_path = File.join(__dir__, "graalpy_wheels.txt")
      all_wheels = File.readlines(index_path).select do |line|
        !line.empty? && !line.start_with?("#")
      end.collect do |line|
        if /([^-]+)-([^-]+)-.*\.whl/ =~ line
          Wheel.new($1.downcase.gsub(/[-_.]+/, "-"), line.strip)
        else
          raise "Invalid wheel name in #{index_path}: #{line.inspect}"
        end
      end
      wheels_by_name = all_wheels.group_by(&:name)
      index_links = wheels_by_name.keys.map do |name|
        "<a href=\"#{name}\">#{name}</a>"
      end
      index_page = Jekyll::PageWithoutAFile.new(site, "", "/wheels", "index.html")
      index_page.content = render index_links
      site.pages << index_page

      wheels_by_name.each do |name, wheels|
        package_links = wheels.collect do |wheel|
          "<a href=\"https://gds.oracle.com/download/graalpy-wheels/#{wheel.filename}\">#{wheel.filename}</a>"
        end
        package_page = Jekyll::PageWithoutAFile.new(site, "", "/wheels/#{name}", "index.html")
        package_page.content = render package_links
        site.pages << package_page
      end
    end

    def render(links)
      TEMPLATE % { content: links.join("\n") }
    end
  end
end
